package com.rentmanager.modules.tenant.infrastructure.daraja;

import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload.StkCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Handles inbound M-Pesa callbacks for the subscription billing flow
 * (initial activation + monthly renewal). Mirror of
 * {@code RentPaymentCallbackService} for the rent flow, including the
 * receipt-missing and duplicate-callback handling.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionPaymentCallbackService {

    private final SubscriptionPaymentCallbackTransactionService txService;

    public void handle(MpesaCallbackPayload payload) {
        StkCallback callback = payload.getBody().getStkCallback();
        String checkoutRequestId = callback.getCheckoutRequestId();

        log.info("Subscription payment M-Pesa callback received. CheckoutRequestID={} ResultCode={}",
                checkoutRequestId, callback.getResultCode());

        if (!callback.isSuccessful()) {
            handleFailure(checkoutRequestId, callback.getResultDesc());
            return;
        }

        String mpesaReceiptNumber = callback.getCallbackMetadata() != null
                ? callback.getCallbackMetadata().getMpesaReceiptNumber()
                : null;

        if (mpesaReceiptNumber == null) {
            handleFailure(checkoutRequestId, "No MpesaReceiptNumber in callback");
            return;
        }

        txService.processSuccessfulCallback(checkoutRequestId, mpesaReceiptNumber);
    }

    private void handleFailure(String checkoutRequestId, String reason) {
        log.warn("Subscription payment M-Pesa callback reported failure. CheckoutRequestID={} Reason={}",
                checkoutRequestId, reason);
        txService.processFailedCallback(checkoutRequestId, reason);
    }
}
