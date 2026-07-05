package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Commits the DEPOSIT_PAID -> FULFILLING status transition in its own
 * short, independently-committed transaction, BEFORE the rest of the
 * fulfillment saga in ReservationFulfillmentOrchestrator begins.
 *
 * ---- Why this exists (see project handoff §1, Findings A and B) ----
 *
 * Previously, markFulfilling() was written as the first step inside the
 * SAME single REQUIRES_NEW transaction that covered the entire saga. If
 * any later step failed, ReservationFulfillmentCompensationService.compensate()
 * ran in ITS OWN separate REQUIRES_NEW transaction — which, under READ
 * COMMITTED, cannot see any of the triggering transaction's uncommitted
 * writes, INCLUDING the not-yet-committed FULFILLING transition. By
 * committing this transition FIRST, independently of everything else,
 * compensate() is guaranteed to always see the reservation in FULFILLING
 * when it runs, no matter what fails afterward.
 *
 * This does NOT make later steps (Clerk/TenantProfile/Lease/Unit)
 * visible to compensate() if THEY haven't committed — Reservation.
 * markFulfillmentFailed() tolerates that separately (decision 2, shape a).
 *
 * ---- Version-conflict handling (decision A, confirmed explicitly) ----
 *
 * A version conflict on THIS flush means another concurrent run already
 * claimed the reservation. Nothing has been created yet at this point,
 * so there's nothing to compensate — but unlike the "not in DEPOSIT_PAID"
 * case below, this is NOT swallowed into a boolean. It is deliberately
 * left uncaught here and rethrown to the caller, preserving the EXACT
 * behavior of the original pre-refactor inline code (which rethrew this
 * same exception type from the same logical point). on() does not catch
 * it either, so it propagates out of on() unchanged — this is the
 * documented, tested contract in
 * ReservationFulfillmentOrchestratorConcurrencyTest. Do not change this
 * to a tolerant return without updating that test's core assertion.
 *
 * Must live in a separate bean from ReservationFulfillmentOrchestrator —
 * self-invocation within the same class bypasses the Spring proxy and
 * @Transactional(REQUIRES_NEW) would silently be ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationFulfillmentStepZeroService {

    private final ReservationRepository reservationRepository;

    /**
     * @return true if this call successfully claimed the reservation (it
     *         is now durably FULFILLING; the caller should proceed with
     *         the rest of the saga); false if the reservation was not in
     *         DEPOSIT_PAID for a reason OTHER than a version conflict
     *         (nothing has been created yet, so the caller just steps
     *         aside without compensating).
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException
     *         if a concurrent run wins the race on this flush — rethrown
     *         deliberately, not swallowed. See class javadoc.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFulfilling(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Reservation not found for fulfillment: " + reservationId));

        try {
            reservation.markFulfilling();
            reservationRepository.save(reservation);
            reservationRepository.flush();
            return true;
        } catch (IllegalStateException ex) {
            log.info("Reservation {} could not be claimed for fulfillment " +
                    "(status guard rejected the transition): {}", reservationId, ex.getMessage());
            return false;
        }
        // ObjectOptimisticLockingFailureException intentionally uncaught here.
    }
}