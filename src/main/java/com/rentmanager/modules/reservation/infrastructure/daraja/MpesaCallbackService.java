package com.rentmanager.modules.reservation.infrastructure.daraja;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.reservation.application.dto.InitiateReservationRequest;
import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.model.Reservation;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import com.rentmanager.modules.reservation.domain.repository.ReservationRepository;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload.StkCallback;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MpesaCallbackService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final ReservationRepository reservationRepository;
    private final UnitRepository unitRepository;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher eventPublisher;
    private final EntityManager entityManager;

    @Transactional
    public void handle(MpesaCallbackPayload payload) {

        StkCallback callback = payload.getBody().getStkCallback();
        String checkoutRequestId = callback.getCheckoutRequestId();

        log.info("M-Pesa callback received. CheckoutRequestID={} ResultCode={}",
                checkoutRequestId, callback.getResultCode());

        // 1. Find PaymentIntent by CheckoutRequestID
        PaymentIntent intent = paymentIntentRepository
                .findByMpesaCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> {
                    log.error("No PaymentIntent found for CheckoutRequestID={}", checkoutRequestId);
                    return new IllegalStateException(
                            "No PaymentIntent found for CheckoutRequestID: " + checkoutRequestId
                    );
                });

        // 1b. Idempotency / late-delivery guard. Three distinct non-PENDING
        // cases have to be told apart here — treating them all as "duplicate,
        // ignore" (as this method used to) is only correct for two of them:
        //
        //   - status == PAID or FAILED: this checkoutRequestId has already
        //     been fully processed by a prior delivery of this exact method.
        //     Safaricom retries callbacks that don't get a fast 200, so this
        //     is a completely expected duplicate webhook delivery. Return
        //     cleanly (instead of letting markPaid/markFailed throw) to avoid
        //     an unnecessary 500 -> Safaricom retry -> 500 loop.
        //
        //   - status == EXPIRED and this callback reports SUCCESS: this is
        //     NOT a duplicate. It means the customer genuinely paid, but the
        //     scheduled stale-intent sweep (PaymentIntentExpirySweepService)
        //     timed this intent out and released the unit before the real
        //     callback arrived. Money has moved but no reservation exists
        //     and the unit may already belong to a different applicant.
        //     Handled explicitly below — never silently dropped.
        //
        //   - status == EXPIRED and this callback reports FAILURE: the sweep
        //     and Safaricom agree the payment didn't go through. Nothing to
        //     reconcile; safe to treat as a no-op duplicate.
        //
        // True concurrent races (two deliveries processed at the exact same
        // instant while still PENDING) are still caught by @Version
        // optimistic locking on PaymentIntent/Reservation below; that case
        // surfaces as a genuine exception on commit and self-heals on the
        // next Safaricom retry, which will then hit one of the branches here.
        if (intent.getStatus() == PaymentIntentStatus.PAID
                || intent.getStatus() == PaymentIntentStatus.FAILED) {
            log.info("Duplicate M-Pesa callback ignored. CheckoutRequestID={} currentStatus={}",
                    checkoutRequestId, intent.getStatus());
            return;
        }

        if (intent.getStatus() == PaymentIntentStatus.EXPIRED) {
            if (!callback.isSuccessful()) {
                log.info("M-Pesa callback for already-expired intent reported failure — nothing to reconcile. " +
                                "CheckoutRequestID={} PaymentIntentId={}",
                        checkoutRequestId, intent.getId());
                return;
            }

            String receiptNumber = callback.getCallbackMetadata() != null
                    ? callback.getCallbackMetadata().getMpesaReceiptNumber()
                    : null;

            intent.markPaidAfterExpiry(receiptNumber);
            try {
                paymentIntentRepository.save(intent);
                entityManager.flush();
            } catch (Exception e) {
                log.error("Unexpected error persisting PAID_AFTER_EXPIRY PaymentIntent. " +
                                "CheckoutRequestID={} PaymentIntentId={}",
                        checkoutRequestId, intent.getId(), e);
                throw e;
            }

            // CRITICAL: money was taken on a unit/reservation attempt that
            // our system had already given up on and released. No automatic
            // ops-alerting channel is wired up yet — this log line is the
            // only signal until one exists. TODO: replace/augment with a
            // real alert (Slack/email/PagerDuty) once that infra is chosen.
            log.error("CRITICAL: M-Pesa payment succeeded AFTER PaymentIntent was already expired and unit " +
                            "released. Requires manual reconciliation/refund review. " +
                            "CheckoutRequestID={} PaymentIntentId={} unitId={} propertyId={} " +
                            "depositAmount={} mpesaReceiptNumber={}",
                    checkoutRequestId, intent.getId(), intent.getUnitId(), intent.getPropertyId(),
                    intent.getDepositAmount(), receiptNumber);
            return;
        }

        // 2. Handle failed payment
        if (!callback.isSuccessful()) {
            log.warn("M-Pesa payment failed. CheckoutRequestID={} Reason={}",
                    checkoutRequestId, callback.getResultDesc());
            intent.markFailed();
            try {
                paymentIntentRepository.save(intent);
                // Force the flush HERE, inside the method, instead of letting it
                // happen silently at @Transactional commit time after handle()
                // has already returned. Without this, any ObjectOptimisticLockingFailureException
                // or constraint violation surfaces outside this method's scope,
                // bypasses this class's logging entirely, and reaches the client
                // as a bare 500 with no stack trace anywhere in the logs.
                entityManager.flush();
            } catch (ObjectOptimisticLockingFailureException e) {
                log.error("Optimistic locking failure marking PaymentIntent FAILED. " +
                                "CheckoutRequestID={} PaymentIntentId={} — likely concurrent callback delivery, " +
                                "will self-heal on Safaricom retry.",
                        checkoutRequestId, intent.getId(), e);
                throw e;
            } catch (Exception e) {
                log.error("Unexpected error persisting failed PaymentIntent. " +
                                "CheckoutRequestID={} PaymentIntentId={}",
                        checkoutRequestId, intent.getId(), e);
                throw e;
            }
            // Release the unit back to VACANT now that the deposit attempt
            // has definitively failed. Guarded internally so a unit that
            // has already moved past PENDING_PAYMENT for any other reason
            // is never touched by a stale/duplicate callback.
            releaseUnitIfPendingPayment(intent.getUnitId(), checkoutRequestId);
            return;
        }

        // 3. Extract receipt number
        String mpesaReceiptNumber = callback.getCallbackMetadata().getMpesaReceiptNumber();
        if (mpesaReceiptNumber == null) {
            log.error("No MpesaReceiptNumber in callback for CheckoutRequestID={}", checkoutRequestId);
            intent.markFailed();
            try {
                paymentIntentRepository.save(intent);
                entityManager.flush();
            } catch (Exception e) {
                log.error("Unexpected error persisting failed PaymentIntent (missing receipt). " +
                                "CheckoutRequestID={} PaymentIntentId={}",
                        checkoutRequestId, intent.getId(), e);
                throw e;
            }
            releaseUnitIfPendingPayment(intent.getUnitId(), checkoutRequestId);
            return;
        }

        // 4. Mark PaymentIntent paid
        intent.markPaid(mpesaReceiptNumber);
        try {
            paymentIntentRepository.save(intent);
            entityManager.flush();
        } catch (ObjectOptimisticLockingFailureException e) {
            log.error("Optimistic locking failure marking PaymentIntent PAID. " +
                            "CheckoutRequestID={} PaymentIntentId={} — likely concurrent callback delivery, " +
                            "will self-heal on Safaricom retry.",
                    checkoutRequestId, intent.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error persisting paid PaymentIntent. " +
                            "CheckoutRequestID={} PaymentIntentId={}",
                    checkoutRequestId, intent.getId(), e);
            throw e;
        }

        // 5. Deserialize form data stored in PaymentIntent
        InitiateReservationRequest formData;
        try {
            formData = objectMapper.readValue(
                    intent.getFormDataJson(),
                    InitiateReservationRequest.class
            );
        } catch (Exception e) {
            log.error("Failed to deserialize form data for PaymentIntent={}",
                    intent.getId(), e);
            throw new IllegalStateException("Failed to deserialize reservation form data", e);
        }

        // 6. Create and persist Reservation
        // propertyId comes from intent.getPropertyId() (stored on PaymentIntent at creation time)
        Reservation reservation = Reservation.create(
                intent.getUnitId(),
                intent.getPropertyId(),
                formData.fullName(),
                formData.phone(),
                formData.email(),
                formData.nationalId(),
                formData.mpesaPhone(),
                formData.moveInDate(),
                intent.getDepositAmount(),
                intent.getId()
        );

        // 7. Mark deposit paid — fires ReservationDepositPaidEvent
        reservation.markDepositPaid(mpesaReceiptNumber);
        try {
            reservationRepository.save(reservation);
            entityManager.flush();
        } catch (Exception e) {
            log.error("Unexpected error persisting Reservation after deposit paid. " +
                            "CheckoutRequestID={} PaymentIntentId={}",
                    checkoutRequestId, intent.getId(), e);
            throw e;
        }

        // NOTE: the unit stays PENDING_PAYMENT here — it is NOT flipped to
        // RESERVED in this method. That transition belongs to
        // ReservationFulfillmentOrchestrator (step 5), which owns the rest
        // of the fulfillment saga triggered by the event published below.
        // Marking it RESERVED here would let this method's transaction
        // commit that state before the saga has actually done anything,
        // and would duplicate a transition the orchestrator already owns.

        // 8. Publish domain events via the DomainEventPublisher abstraction
        eventPublisher.publishAll(reservation.pullDomainEvents());

        log.info("Reservation created and deposit paid event fired. reservationId={} receipt={}",
                reservation.getId(), mpesaReceiptNumber);
    }

    /**
     * Releases a unit stuck in PENDING_PAYMENT back to VACANT after a
     * confirmed failed/incomplete M-Pesa payment. Uses a locking read
     * (findByIdForUpdate) rather than a plain findById because this method
     * runs from an inbound webhook — an untrusted, potentially-retried,
     * potentially-concurrent entry point — and we want the same
     * serialization guarantee here as at reservation-initiation time.
     *
     * Internally guarded by Unit.releasePendingPayment(), which only acts
     * if the unit is currently PENDING_PAYMENT. This makes the call safe
     * to issue even on a stale or duplicate callback delivery: if the unit
     * has already moved on (e.g. a separate successful reservation reached
     * RESERVED in the meantime), this is a no-op rather than incorrectly
     * vacating a unit out from under a different, legitimate tenant.
     */
    private void releaseUnitIfPendingPayment(UUID unitId, String checkoutRequestId) {
        try {
            Unit unit = unitRepository.findByIdForUpdate(unitId)
                    .orElseThrow(() -> new IllegalStateException("Unit not found: " + unitId));

            if (unit.getOccupancyStatus() == UnitOccupancyStatus.PENDING_PAYMENT) {
                unit.releasePendingPayment(checkoutRequestId);
                unitRepository.save(unit);
                eventPublisher.publishAll(unit.pullDomainEvents());
                entityManager.flush();
                log.info("Unit released back to VACANT after failed payment. unitId={} CheckoutRequestID={}",
                        unitId, checkoutRequestId);
            } else {
                log.info("Unit not in PENDING_PAYMENT, skipping release. unitId={} currentStatus={} CheckoutRequestID={}",
                        unitId, unit.getOccupancyStatus(), checkoutRequestId);
            }
        } catch (Exception e) {
            // Deliberately does not rethrow: the PaymentIntent has already
            // been correctly marked FAILED and flushed above, which is the
            // financially-critical write. A failure to release the unit is
            // a lesser problem (a self-healing one, given the scheduled
            // stale-intent sweep will catch it) and should not turn into a
            // 500 back to Safaricom, which would trigger a retry loop for
            // a webhook that has otherwise been fully and correctly handled.
            log.error("Failed to release unit after failed payment — will self-heal via " +
                            "scheduled stale PaymentIntent sweep. unitId={} CheckoutRequestID={}",
                    unitId, checkoutRequestId, e);
        }
    }
}