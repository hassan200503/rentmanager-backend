package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.ClerkUserCreationResult;
import com.rentmanager.modules.identity.clerk.SignInTokenResult;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.reservation.application.command.validator.ReservationFulfillmentValidator;
import com.rentmanager.modules.reservation.domain.event.ReservationDepositPaidEvent;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the full Phase 4 chain as a compensating saga:
 * 0. Claim the reservation for fulfillment (DEPOSIT_PAID -> FULFILLING),
 *    committed independently — see ReservationFulfillmentStepZeroService.
 * 1. Create tenant account in Clerk (track if NEWLY created vs reused);
 *    no password is set — authentication uses single-use sign-in tokens.
 * 2. Create a Clerk sign-in token (7-day expiry) and send the link via SMS
 * 3. Create or reuse TenantProfile (track if NEWLY created vs reused)
 * 4. Auto-create Lease in PENDING_ACTIVATION
 * 5. Mark Unit reserved
 * 6. Complete the Reservation (FULFILLING -> COMPLETED)
 *
 * Runs AFTER_COMMIT so a failure here can never roll back the confirmed
 * M-Pesa payment recorded by MpesaCallbackService.
 *
 * ---- Transaction-visibility fix (project handoff §1, Findings A & B) ----
 *
 * Step 0 is now committed by ReservationFulfillmentStepZeroService in ITS
 * OWN independent transaction, BEFORE steps 1-6 begin here. This
 * guarantees compensate() always sees FULFILLING, no matter what fails
 * afterward — closing the "permanently stuck, no failure marker" failure
 * mode from Finding B.
 *
 * The generic catch (Exception e) below now RETHROWS (wrapped) after
 * calling compensate() — previously it returned normally, letting Spring
 * commit the transaction regardless of the failure. This was a deliberate
 * decision (decision 1), confirmed explicitly, not an incidental change.
 *
 * ---- Concurrency handling (see ReservationFulfillmentOrchestratorConcurrencyTest) ----
 *
 * Two collision points, handled asymmetrically on purpose:
 *
 * - Step 0: handled entirely inside ReservationFulfillmentStepZeroService.
 *   A version conflict there means another run already claimed this
 *   reservation; nothing has been created yet, so there's nothing to
 *   compensate. That service deliberately does NOT catch
 *   ObjectOptimisticLockingFailureException — it propagates out of
 *   stepZeroService.markFulfilling(...), and on() does not catch it
 *   either, so it propagates out of on() unchanged (decision A —
 *   preserves the exact rethrow behavior of the original pre-refactor
 *   inline code, rather than swallowing it into a boolean). This is
 *   asserted directly by ReservationFulfillmentOrchestratorConcurrencyTest.
 *
 * - Step 6 (complete flush): by this point real work (Clerk/Lease/Unit)
 *   may have been created by THIS run, so compensate() IS called before
 *   rethrowing. Rethrowing here is safe because on() only ever runs as
 *   an AFTER_COMMIT transactional-event-listener callback — Spring's
 *   TransactionSynchronizationUtils catches and logs Throwable from
 *   listener callbacks without affecting the already-committed outer
 *   transaction.
 *
 * ---- Event-publishing fix (broader event-publish sweep, item 4.3) ----
 *
 * Step 3 now passes reservationId.toString() as TenantProfile.create()'s
 * new correlationId parameter (matching the correlation id already used
 * for the sibling Unit/Lease events in this same saga step) and calls
 * eventPublisher.publishAll(tenantProfile.pullDomainEvents()) immediately
 * after save. TenantProfile.create() now registers a TenantProfileCreatedEvent
 * that previously did not exist; without this publish call it would have
 * been registered and silently discarded at commit, the same shape as the
 * pre-fix lease bug (project handoff §2.9). The existingProfile branch
 * intentionally does not publish — reusing an existing profile registers
 * no event, so there's nothing to pull there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationFulfillmentOrchestrator {

    private final ReservationRepository reservationRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final ClerkService clerkService;
    private final SmsService smsService;
    private final ReservationFulfillmentValidator fulfillmentValidator;
    private final DomainEventPublisher eventPublisher;
    private final ReservationFulfillmentCompensationService compensationService;
    private final ReservationFulfillmentStepZeroService stepZeroService;
    private final RentLedgerApplicationService rentLedgerApplicationService;

    // TODO: confirm — does a default lease term length exist anywhere
    // (e.g. tenant-configurable per property), or is 12 months a safe
    // hardcoded default for now?
    private static final int DEFAULT_LEASE_TERM_MONTHS = 12;
    private static final int SIGN_IN_TOKEN_EXPIRY_SECONDS = 604800; // 7 days

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ReservationDepositPaidEvent event) {

        UUID reservationId = event.getAggregateId();

        // ---- Step 0: claim the reservation, committed independently ----
        // A version conflict here propagates directly out of on() —
        // see class javadoc "Concurrency handling" above. Deliberately
        // NOT wrapped in try/catch at this call site.
        boolean claimed = stepZeroService.markFulfilling(reservationId);
        if (!claimed) {
            return; // Already claimed by another run, or unexpected state — nothing to do.
        }

        // Re-fetch inside THIS transaction/session: stepZeroService
        // committed in its own separate transaction, so this is a fresh
        // read reflecting the FULFILLING status and bumped version.
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation not found for fulfillment: " + reservationId));

        boolean leaseExists = leaseRepository
                .findByUnitIdAndStatus(event.getUnitId(), LeaseStatus.PENDING_ACTIVATION)
                .isPresent();

        fulfillmentValidator.validate(reservation, true, leaseExists);

        // Tracks what THIS run actually did, so compensation only undoes
        // what it created — never a reused Clerk account or TenantProfile.
        SagaState saga = new SagaState();

        try {
            // ---- Step 1: Clerk account (no password — sign-in tokens only) ----
            ClerkUserCreationResult clerkResult = clerkService.createTenantUser(
                    event.getFullName(),
                    event.getEmail(),
                    event.getPhone()
            );
            String clerkUserId = clerkResult.clerkUserId();
            saga.clerkUserId = clerkUserId;
            saga.clerkUserCreatedThisRun = clerkResult.newlyCreated();

            // Backend-authoritative persona write for a brand-new renter.
            // Best-effort and only when the account was actually created by
            // THIS run — a reused account already has its persona managed
            // elsewhere, and a failure here must not fail the fulfillment
            // saga (the existing deleteUser compensation already handles the
            // failure aftermath if the saga itself fails).
            if (clerkResult.newlyCreated()) {
                clerkService.setPublicMetadata(
                        clerkUserId,
                        Map.of(ClerkService.USER_TYPE_KEY, "renter")
                );
            }

            // ---- Step 2: Sign-in token + SMS ----
            SignInTokenResult tokenResult = clerkService.createSignInToken(
                    clerkUserId,
                    SIGN_IN_TOKEN_EXPIRY_SECONDS
            );
            smsService.sendSignInLink(event.getPhone(), tokenResult.url());

            // ---- Step 3: TenantProfile ----
            Unit unit = unitRepository.findById(event.getUnitId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Unit not found for lease creation: " + event.getUnitId()));

            UUID landlordTenantId = unit.getTenantId();

            var existingProfile = tenantProfileRepository
                    .findByTenantIdAndClerkUserId(landlordTenantId, clerkUserId);

            TenantProfile tenantProfile;
            if (existingProfile.isPresent()) {
                tenantProfile = existingProfile.get();
                tenantProfile.updateDetails(
                        event.getFullName(),
                        event.getEmail(),
                        event.getPhone(),
                        event.getNationalId()
                );
                tenantProfileRepository.save(tenantProfile);
                saga.tenantProfileCreatedThisRun = false;
            } else {
                TenantProfile newProfile = TenantProfile.create(
                        landlordTenantId,
                        clerkUserId,
                        event.getFullName(),
                        event.getEmail(),
                        event.getPhone(),
                        event.getNationalId(),
                        reservationId.toString()
                );
                List<DomainEvent> profileEvents = newProfile.pullDomainEvents();
                tenantProfile = tenantProfileRepository.save(newProfile);
                eventPublisher.publishAll(profileEvents);
                saga.tenantProfileCreatedThisRun = true;
            }
            saga.tenantProfileId = tenantProfile.getId();
            UUID tenantProfileId = tenantProfile.getId();

            // ---- Step 4: Lease ----
            // Reuse a lease this unit already has awaiting activation instead of
            // creating a second one.
            //
            // Until now this step created unconditionally, and the only thing
            // stopping a duplicate was the Step 0 claim: markFulfilling() refuses
            // a reservation that is not DEPOSIT_PAID, so a repeated event could
            // not get this far. That is a single point of protection for the most
            // expensive mistake available here -- two leases on one unit means two
            // rent ledgers and a renter billed twice -- and it is also precisely
            // what makes retrying a failed fulfilment unsafe today (TD-163).
            // Making the step idempotent in its own right removes both problems.
            java.util.Optional<Lease> awaitingActivation =
                    leaseRepository.findByUnitIdAndStatus(event.getUnitId(), LeaseStatus.PENDING_ACTIVATION);

            Lease lease;
            if (awaitingActivation.isPresent()) {
                lease = awaitingActivation.get();
                if (!tenantProfileId.equals(lease.getTenantProfileId())) {
                    // Someone else's lease is already pending on this unit. Not a
                    // duplicate of ours to reuse -- fail loudly rather than create
                    // a second lease or silently attach this renter to somebody
                    // else's tenancy.
                    throw new IllegalStateException(
                            "Unit already has a lease pending activation for a different renter. unitId="
                                    + event.getUnitId());
                }
                log.warn("Fulfilment re-entered with a lease already pending activation; reusing it. "
                        + "reservationId={} leaseId={}", reservationId, lease.getId());
                saga.leaseId = lease.getId();
                // Not created by THIS run, so compensation must not cancel it.
                saga.leaseCreated = false;
            } else {
                String leaseNumber = generateLeaseNumber(unit);

                LocalDate startDate = event.getMoveInDate();
                LocalDate endDate = startDate.plusMonths(DEFAULT_LEASE_TERM_MONTHS);

                lease = Lease.createPendingActivation(
                        landlordTenantId,
                        event.getPropertyId(),
                        event.getUnitId(),
                        tenantProfileId,
                        leaseNumber,
                        LeaseType.FIXED_TERM,
                        BillingCycle.MONTHLY,
                        startDate,
                        endDate,
                        unit.getRentAmount(),
                        event.getDepositAmount(),
                        null, // lateFeeAmount — TODO: confirm default policy
                        null, // gracePeriodDays — TODO: confirm default policy
                        false // autoRenew — TODO: confirm default
                );
                leaseRepository.save(lease);
                eventPublisher.publishAll(lease.pullDomainEvents());
                saga.leaseId = lease.getId();
                saga.leaseCreated = true;
            }

            // Record the deposit transaction in the rent ledger immediately,
            // so it appears live in the transactions dashboard. The deposit
            // was already collected via M-Pesa STK Push during reservation.
            // Note: if no ledger entry exists yet (lease not activated),
            // postDeposit is a no-op. The deposit is posted by
            // LeaseActivationOrchestrator.onLeaseActivated when the lease
            // is activated.
            if (lease.getSecurityDeposit() != null
                    && lease.getSecurityDeposit().compareTo(BigDecimal.ZERO) > 0) {
                rentLedgerApplicationService.postDeposit(
                        landlordTenantId,
                        "reservation-deposit-" + reservationId,
                        lease.getId(),
                        lease.getSecurityDeposit(),
                        reservation.getMpesaReceiptNumber()
                );
            }
            saga.depositPosted = true;

            // ---- Step 5: Unit reserved ----
            unit.markReserved(reservation.getId().toString());
            unitRepository.save(unit);
            eventPublisher.publishAll(unit.pullDomainEvents());
            saga.unitId = unit.getId();
            saga.unitReserved = true;

            // ---- Step 6: Complete reservation ----
            reservation.complete(clerkUserId);
            // Pull events from the original object before save() returns a
            // rehydrated copy that has no transient domain events.
            List<DomainEvent> reservationEvents = reservation.pullDomainEvents();
            try {
                reservation = reservationRepository.save(reservation);
                reservationRepository.flush();
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.error("Version conflict completing reservation after saga work already " +
                        "done — compensating. reservationId={}", reservationId, ex);
                compensationService.compensate(saga, reservationId, ex);
                throw ex;
            }

            eventPublisher.publishAll(reservationEvents);

            log.info("Reservation fulfilled. reservationId={} leaseId={} clerkUserId={}",
                    reservation.getId(), saga.leaseId, clerkUserId);

        } catch (ObjectOptimisticLockingFailureException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Reservation fulfillment failed, beginning compensation. reservationId={}",
                    reservationId, e);
            compensationService.compensate(saga, reservationId, e);
            throw new ReservationFulfillmentFailedException(
                    "Reservation fulfillment failed after compensation; rolling back saga transaction. " +
                            "reservationId=" + reservationId, e);
        }
    }

    private String generateLeaseNumber(Unit unit) {
        return "LSE-" + unit.getUnitNumber() + "-" + System.currentTimeMillis();
    }
}