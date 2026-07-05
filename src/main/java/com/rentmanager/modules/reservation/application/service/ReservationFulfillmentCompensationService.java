package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Runs compensation in a brand-new transaction, isolated from whatever
 * transaction the triggering failure poisoned. Must live in a separate
 * bean from ReservationFulfillmentOrchestrator — self-invocation within
 * the same class would bypass the Spring proxy and REQUIRES_NEW would
 * silently be ignored.
 *
 * NOTE (project handoff §1): this transaction cannot see steps 1-5's
 * writes (Clerk/TenantProfile/Lease/Unit) if they haven't committed in
 * the triggering saga's transaction — that's an inherent limit of running
 * compensation separately, not a bug to "fix" here. What IS fixed: the
 * reservation's FULFILLING status is now committed independently and
 * early by ReservationFulfillmentStepZeroService, so this method's
 * final status-transition step below is now reliable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationFulfillmentCompensationService {

    private final ReservationRepository reservationRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final ClerkService clerkService;
    private final DomainEventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void compensate(SagaState saga, UUID reservationId, Exception originalError) {

        if (saga.unitReserved && saga.unitId != null) {
            try {
                Unit unit = unitRepository.findById(saga.unitId).orElse(null);
                if (unit != null) {
                    unit.releaseReservation(reservationId.toString());
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
                    lease.cancel("Reservation fulfillment failed: " + originalError.getClass().getSimpleName());
                    leaseRepository.save(lease);
                    eventPublisher.publishAll(lease.pullDomainEvents());
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
            try {
                clerkService.deleteUser(saga.clerkUserId);
            } catch (Exception ex) {
                log.error("Compensation FAILED to delete Clerk user. clerkUserId={} — " +
                        "MANUAL CLEANUP REQUIRED", saga.clerkUserId, ex);
            }
        }

        try {
            Reservation freshReservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Reservation vanished during compensation: " + reservationId));
            boolean transitioned = freshReservation.markFulfillmentFailed(
                    originalError.getClass().getSimpleName() + ": " + originalError.getMessage());
            if (transitioned) {
                reservationRepository.save(freshReservation);
                log.info("Compensation: reservation {} marked FULFILLMENT_FAILED.", reservationId);
            } else {
                // Tolerated no-op — see Reservation.markFulfillmentFailed() javadoc.
                // With step 0 now committing independently, this should be rare;
                // if it shows up often, that's a signal something upstream changed.
                log.info("Compensation: reservation {} was not transitioned to " +
                                "FULFILLMENT_FAILED (status={} at time of compensation) — " +
                                "no action needed/possible from this transaction's view.",
                        reservationId, freshReservation.getStatus());
            }
        } catch (Exception ex) {
            log.error("CRITICAL: failed to mark reservation as FULFILLMENT_FAILED after " +
                    "compensation. reservationId={} — this reservation is now in an UNKNOWN " +
                    "state and requires immediate manual investigation", reservationId, ex);
        }

        log.error("Reservation fulfillment compensation complete. reservationId={} — " +
                "PAYMENT WAS RECEIVED, manual follow-up required (refund or retry).", reservationId);
    }
}