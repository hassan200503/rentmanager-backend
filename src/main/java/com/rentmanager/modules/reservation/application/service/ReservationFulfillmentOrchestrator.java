package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.identity.clerk.ClerkUserCreationResult;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.notification.sms.SmsService;
import com.rentmanager.modules.reservation.application.command.validator.ReservationFulfillmentValidator;
import com.rentmanager.modules.reservation.domain.event.ReservationDepositPaidEvent;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Orchestrates the full Phase 4 chain as a compensating saga:
 * 0. Claim the reservation for fulfillment (DEPOSIT_PAID -> FULFILLING),
 *    committed independently — see ReservationFulfillmentStepZeroService.
 * 1. Create tenant account in Clerk (track if NEWLY created vs reused)
 * 2. Send SMS with credentials
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

    // TODO: confirm — does a default lease term length exist anywhere
    // (e.g. tenant-configurable per property), or is 12 months a safe
    // hardcoded default for now?
    private static final int DEFAULT_LEASE_TERM_MONTHS = 12;

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
            // ---- Step 1: Clerk account ----
            String password = generateTemporaryPassword();
            ClerkUserCreationResult clerkResult = clerkService.createTenantUser(
                    event.getFullName(),
                    event.getEmail(),
                    event.getPhone(),
                    password
            );
            String clerkUserId = clerkResult.clerkUserId();
            saga.clerkUserId = clerkUserId;
            saga.clerkUserCreatedThisRun = clerkResult.newlyCreated();

            // ---- Step 2: SMS ----
            if (clerkResult.newlyCreated()) {
                smsService.sendCredentials(event.getPhone(), password);
            } else {
                smsService.sendReservationConfirmed(event.getPhone());
            }

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
                saga.tenantProfileCreatedThisRun = false;
            } else {
                tenantProfile = tenantProfileRepository.save(TenantProfile.create(
                        landlordTenantId,
                        clerkUserId,
                        event.getFullName(),
                        event.getEmail(),
                        event.getPhone(),
                        event.getNationalId()
                ));
                saga.tenantProfileCreatedThisRun = true;
            }
            saga.tenantProfileId = tenantProfile.getId();
            UUID tenantProfileId = tenantProfile.getId();

            // ---- Step 4: Lease ----
            String leaseNumber = generateLeaseNumber(unit);

            LocalDate startDate = event.getMoveInDate();
            LocalDate endDate = startDate.plusMonths(DEFAULT_LEASE_TERM_MONTHS);

            Lease lease = Lease.createPendingActivation(
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

            // ---- Step 5: Unit reserved ----
            unit.markReserved(reservation.getId().toString());
            unitRepository.save(unit);
            eventPublisher.publishAll(unit.pullDomainEvents());
            saga.unitId = unit.getId();
            saga.unitReserved = true;

            // ---- Step 6: Complete reservation ----
            try {
                reservation.complete(clerkUserId);
                reservation = reservationRepository.save(reservation);
                reservationRepository.flush();
            } catch (ObjectOptimisticLockingFailureException ex) {
                log.error("Version conflict completing reservation after saga work already " +
                        "done — compensating. reservationId={}", reservationId, ex);
                compensationService.compensate(saga, reservationId, ex);
                throw ex;
            }

            eventPublisher.publishAll(reservation.pullDomainEvents());

            log.info("Reservation fulfilled. reservationId={} leaseId={} clerkUserId={}",
                    reservation.getId(), lease.getId(), clerkUserId);

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

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String generateLeaseNumber(Unit unit) {
        return "LSE-" + unit.getUnitNumber() + "-" + System.currentTimeMillis();
    }
}