package com.rentmanager.modules.rentledger.infrastructure.daraja;

import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload.StkCallback;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles Daraja STK-push callbacks for recurring rent payments. This is
 * "Phase 3's own callback service, not MpesaCallbackService" referenced in
 * {@code RentTransactionSource.MPESA}'s javadoc — a deliberately separate
 * class from the deposit flow's {@code MpesaCallbackService} rather than
 * a shared/generalized one, since the two flows touch entirely different
 * aggregates (RentPaymentRequest + RentLedgerEntry here, vs. PaymentIntent
 * + Reservation + Unit there) and merging them would just reintroduce a
 * conditional branch for "which kind of payment is this" inside one class.
 *
 * Reuses {@code MpesaCallbackPayload} from the reservation module's Daraja
 * package since the callback JSON shape is identical regardless of what
 * the payment was for — this is Daraja's payload contract, not a
 * reservation-specific concept.
 *
 * IDEMPOTENCY: single-layer here (status != PENDING is treated as an
 * already-processed duplicate) rather than MpesaCallbackService's
 * three-way EXPIRED/PAID/FAILED handling, since RentPaymentRequest has no
 * EXPIRED state yet (see RentPaymentRequestStatus javadoc) — there is no
 * "expired but a real payment landed late" case to reconcile until that
 * state exists. A second idempotency layer already exists downstream:
 * RentLedgerApplicationService.applyTransaction() checks the M-Pesa
 * receipt number as externalReference before applying, so even a
 * duplicate that somehow got past this check would not double-post.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RentPaymentCallbackService {

    private final RentPaymentRequestRepository rentPaymentRequestRepository;
    private final RentLedgerApplicationService rentLedgerApplicationService;

    @Transactional
    public void handle(MpesaCallbackPayload payload) {
        StkCallback callback = payload.getBody().getStkCallback();
        String checkoutRequestId = callback.getCheckoutRequestId();

        log.info("Rent payment M-Pesa callback received. CheckoutRequestID={} ResultCode={}",
                checkoutRequestId, callback.getResultCode());

        RentPaymentRequest request = rentPaymentRequestRepository
                .findByMpesaCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> {
                    log.error("No RentPaymentRequest found for CheckoutRequestID={}", checkoutRequestId);
                    return new RentLedgerStateException(
                            "No RentPaymentRequest found for CheckoutRequestID: " + checkoutRequestId,
                            ErrorCode.RESOURCE_NOT_FOUND
                    );
                });

        if (request.getStatus() != RentPaymentRequestStatus.PENDING) {
            log.info("Duplicate rent payment M-Pesa callback ignored. CheckoutRequestID={} currentStatus={}",
                    checkoutRequestId, request.getStatus());
            return;
        }

        if (!callback.isSuccessful()) {
            log.warn("Rent payment M-Pesa payment failed. CheckoutRequestID={} Reason={}",
                    checkoutRequestId, callback.getResultDesc());
            request.markFailed();
            rentPaymentRequestRepository.save(request);
            return;
        }

        String mpesaReceiptNumber = callback.getCallbackMetadata() != null
                ? callback.getCallbackMetadata().getMpesaReceiptNumber()
                : null;

        if (mpesaReceiptNumber == null) {
            log.error("No MpesaReceiptNumber in rent payment callback for CheckoutRequestID={}", checkoutRequestId);
            request.markFailed();
            rentPaymentRequestRepository.save(request);
            return;
        }

        request.markPaid(mpesaReceiptNumber);
        rentPaymentRequestRepository.save(request);

        String correlationId = "rent-payment-" + request.getId();

        rentLedgerApplicationService.applyTransaction(
                request.getTenantId(),
                correlationId,
                request.getRentLedgerEntryId(),
                RentTransactionType.PAYMENT,
                request.getAmount(),
                mpesaReceiptNumber,
                RentTransactionSource.MPESA,
                "SYSTEM",
                LocalDateTime.now()
        );

        log.info("Rent payment applied to ledger. rentLedgerEntryId={} receipt={}",
                request.getRentLedgerEntryId(), mpesaReceiptNumber);
    }
}
