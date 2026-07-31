package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.modules.tenant.application.service.SubscriptionC2bPaymentService;
import com.rentmanager.modules.tenant.infrastructure.daraja.C2BPaymentConfirmationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives C2B (Paybill) confirmations from Daraja - the collection leg of
 * the M-Pesa Ratiba autobilling flow. The URL is registered against the
 * Paybill (Daraja portal / RegisterURL API), so there is no secret path
 * segment; matching is fail-closed in {@code SubscriptionC2bPaymentService}
 * (reference + exact fee match required, everything else lands in
 * {@code unmatched_payments} for manual reconciliation).
 *
 * <p>Always returns 200 so Safaricom does not retry an already-delivered
 * confirmation; processing errors are logged and redelivery is handled by
 * the TransID idempotency guard.</p>
 *
 * POST /api/v1/public/subscription-billing/c2b/confirmation
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/subscription-billing/c2b")
@RequiredArgsConstructor
public class C2BConfirmationCallbackController {

    private final SubscriptionC2bPaymentService subscriptionC2bPaymentService;

    @PostMapping("/confirmation")
    public ResponseEntity<Void> confirmation(@RequestBody C2BPaymentConfirmationPayload payload) {
        try {
            subscriptionC2bPaymentService.handleConfirmation(payload);
        } catch (Exception e) {
            log.error("Failed to process C2B confirmation. transId={}", payload.transId(), e);
        }
        return ResponseEntity.ok().build();
    }
}
