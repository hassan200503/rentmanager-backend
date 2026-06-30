package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Orchestrates the full Phase 4 chain as a compensating saga:
 *   1. Create tenant account in Clerk (track if NEWLY created vs reused)
 *   2. Send SMS with credentials
 *   3. Create or reuse TenantProfile (track if NEWLY created vs reused)
 *   4. Auto-create Lease in PENDING_ACTIVATION
 *   5. Mark Unit reserved
 *   6. Complete the Reservation (FULFILLING -> COMPLETED)
 *
 * Runs AFTER_COMMIT so a failure here can never roll back the confirmed
 * M-Pesa payment recorded by MpesaCallbackService.
 *
 * If any step fails, compensating actions undo ONLY what THIS run actually
 * created (never touching reused Clerk accounts or TenantProfiles from a
 * prior successful reservation), then the Reservation is marked
 * FULFILLMENT_FAILED — not CANCELLED, because the deposit was genuinely
 * paid and a human must follow up.
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

    // TODO: confirm — does a default lease term length exist anywhere
    // (e.g. tenant-configurable per property), or is 12 months a safe
    // hardcoded default for now?
    private static final int DEFAULT_LEASE_TERM_MONTHS = 12;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReservationDepositPaidEvent event) {

        UUID reservationId = event.getAggregateId();

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
            reservation.markFulfilling();
            reservationRepository.save(reservation);

            // ---- Step 1: Clerk account ----
            boolean clerkUserAlreadyExisted = clerkService.existsByEmail(event.getEmail());
            String password = generateTemporaryPassword();
            String clerkUserId = clerkService.createTenantUser(
                    event.getFullName(),
                    event.getEmail(),
                    event.getPhone(),
                    password
            );
            saga.clerkUserId = clerkUserId;
            saga.clerkUserCreatedThisRun = !clerkUserAlreadyExisted;

            // ---- Step 2: SMS ----
            // Deliberately NOT tracked for compensation — an SMS can't be
            // un-sent, and sending one extra message on a later compensated
            // retry is harmless compared to the complexity of suppressing it.
            smsService.sendCredentials(event.getPhone(), password);

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
            reservation.complete(clerkUserId);
            reservationRepository.save(reservation);
            eventPublisher.publishAll(reservation.pullDomainEvents());

            log.info("Reservation fulfilled. reservationId={} leaseId={} clerkUserId={}",
                    reservation.getId(), lease.getId(), clerkUserId);

        } catch (Exception e) {
            log.error("Reservation fulfillment failed, beginning compensation. reservationId={}",
                    reservationId, e);
            compensate(saga, reservation, e);
        }
    }

    /**
     * Undoes whatever this run actually completed, in reverse order, then
     * marks the reservation as FULFILLMENT_FAILED. Each compensating step is
     * independently guarded so one failing doesn't block the others —
     * compensation should do its best to clean up everything possible.
     */
    private void compensate(SagaState saga, Reservation reservation, Exception originalError) {

        if (saga.unitReserved && saga.unitId != null) {
            try {
                Unit unit = unitRepository.findById(saga.unitId).orElse(null);
                if (unit != null) {
                    unit.releaseReservation(reservation.getId().toString());
                    unitRepository.save(unit);
                    eventPublisher.publishAll(unit.pullDomainEvents());
                    log.info("Compensation: released unit reservation. unitId={}", saga.unitId);
                }
            } catch (Exception ex) {
                log.error("Compensation FAILED to release unit. unitId={} — MANUAL CLEANUP REQUIRED",
                        saga.unitId, ex);
            }
        }

        if (saga.leaseCreated && saga.leaseId != null) {
            try {
                leaseRepository.findById(saga.leaseId).ifPresent(lease -> {
                    lease.cancel();
                    leaseRepository.save(lease);
                });
                log.info("Compensation: cancelled lease. leaseId={}", saga.leaseId);
            } catch (Exception ex) {
                log.error("Compensation FAILED to cancel lease. leaseId={} — MANUAL CLEANUP REQUIRED",
                        saga.leaseId, ex);
            }
        }

        if (saga.tenantProfileCreatedThisRun && saga.tenantProfileId != null) {
            try {
                tenantProfileRepository.deleteById(saga.tenantProfileId);
                log.info("Compensation: deleted newly-created TenantProfile. tenantProfileId={}",
                        saga.tenantProfileId);
            } catch (Exception ex) {
                log.error("Compensation FAILED to delete TenantProfile. tenantProfileId={} — " +
                        "MANUAL CLEANUP REQUIRED", saga.tenantProfileId, ex);
            }
        }

        if (saga.clerkUserCreatedThisRun && saga.clerkUserId != null) {
            // ClerkService.deleteUser is itself best-effort / non-throwing,
            // so no try/catch needed here specifically, but kept for safety
            // against any future change to that contract.
            try {
                clerkService.deleteUser(saga.clerkUserId);
            } catch (Exception ex) {
                log.error("Compensation FAILED to delete Clerk user. clerkUserId={} — " +
                        "MANUAL CLEANUP REQUIRED", saga.clerkUserId, ex);
            }
        }

        try {
            Reservation freshReservation = reservationRepository.findById(reservation.getId())
                    .orElse(reservation);
            freshReservation.markFulfillmentFailed(
                    originalError.getClass().getSimpleName() + ": " + originalError.getMessage());
            reservationRepository.save(freshReservation);
        } catch (Exception ex) {
            log.error("CRITICAL: failed to mark reservation as FULFILLMENT_FAILED after " +
                    "compensation. reservationId={} — this reservation is now in an UNKNOWN " +
                    "state and requires immediate manual investigation", reservation.getId(), ex);
        }

        log.error("Reservation fulfillment compensation complete. reservationId={} — " +
                        "PAYMENT WAS RECEIVED, manual follow-up required (refund or retry).",
                reservation.getId());
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
        // TODO: confirm desired lease number format/uniqueness strategy —
        // this is a placeholder, not validated against existing conventions.
        return "LSE-" + unit.getUnitNumber() + "-" + System.currentTimeMillis();
    }

    /**
     * Mutable tracker for what THIS saga run actually accomplished, used
     * exclusively to scope compensation correctly if a later step fails.
     */
    private static class SagaState {
        String clerkUserId;
        boolean clerkUserCreatedThisRun;

        UUID tenantProfileId;
        boolean tenantProfileCreatedThisRun;

        UUID leaseId;
        boolean leaseCreated;

        UUID unitId;
        boolean unitReserved;
    }
}