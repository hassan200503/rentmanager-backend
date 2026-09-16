package com.rentmanager.modules.deposit.api.controller;

import com.rentmanager.modules.deposit.application.service.DepositCommandService;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Receives Safaricom STK push callbacks for deposit refund authorisations.
 * When a landlord authorises a deposit refund via M-Pesa PIN, Safaricom POSTs
 * the result here and the deposit is automatically recorded as refunded.
 *
 * <p>Security: the callback URL embeds a shared secret as a path segment.
 * A constant-time comparison prevents timing attacks. Mismatches return 404
 * (not 401) to avoid leaking that the endpoint exists.
 *
 * <p>This endpoint is intentionally public (no Spring Security auth) — it is
 * called by Safaricom's servers, not by authenticated users.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/deposits/refund")
public class DepositRefundCallbackController {

    private final DepositCommandService depositCommandService;
    private final DarajaProperties darajaProperties;

    @PostMapping("/callback/{secret}")
    public ResponseEntity<Map<String, String>> callback(
            @PathVariable String secret,
            @RequestBody MpesaCallbackPayload payload
    ) {
        if (!isSecretValid(secret)) {
            log.warn("Deposit refund callback received with invalid secret — ignoring");
            return ResponseEntity.notFound().build();
        }

        MpesaCallbackPayload.StkCallback stk = payload.getBody() != null
                ? payload.getBody().getStkCallback()
                : null;

        if (stk == null || stk.getCheckoutRequestId() == null) {
            log.warn("Deposit refund callback has no stkCallback or CheckoutRequestID — ignoring");
            return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Accepted"));
        }

        String checkoutRequestId = stk.getCheckoutRequestId();

        if (!stk.isSuccessful()) {
            log.info("Deposit refund STK push failed. CheckoutRequestID={} ResultCode={} ResultDesc={}",
                    checkoutRequestId, stk.getResultCode(), stk.getResultDesc());
            // Leave the pending state — landlord can retry or cancel from the UI.
            return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Accepted"));
        }

        String mpesaReceipt = stk.getCallbackMetadata() != null
                ? stk.getCallbackMetadata().getMpesaReceiptNumber()
                : null;

        if (mpesaReceipt == null) {
            log.error("Deposit refund callback success but no MpesaReceiptNumber. CheckoutRequestID={}",
                    checkoutRequestId);
            return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Accepted"));
        }

        try {
            depositCommandService.completeRefundFromCallback(checkoutRequestId, mpesaReceipt);
        } catch (Exception ex) {
            log.error("Failed to complete deposit refund from callback. CheckoutRequestID={} receipt={}",
                    checkoutRequestId, mpesaReceipt, ex);
        }

        return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Accepted"));
    }

    private boolean isSecretValid(String provided) {
        String expected = darajaProperties.getCallbackSecret();
        if (expected == null || expected.isBlank() || provided == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }
}
