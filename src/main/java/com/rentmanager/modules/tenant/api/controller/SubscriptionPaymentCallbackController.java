package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.reservation.infrastructure.daraja.MpesaCallbackPayload;
import com.rentmanager.modules.tenant.infrastructure.daraja.SubscriptionPaymentCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Safaricom posts here after a subscription-billing STK push (initial
 * activation or monthly renewal) completes or is cancelled. Deliberately a
 * separate, public (/api/v1/public/**, permitAll) controller from
 * {@code SubscriptionBillingController} - same split rationale as
 * {@code RentPaymentCallbackController} vs {@code RentPaymentController}.
 *
 * Secret-gating is identical to the rent/deposit callbacks: the shared
 * daraja.callback-secret as a path segment, constant-time compared via
 * MessageDigest.isEqual, mismatch returns 404 (not 401/403). One platform
 * trust boundary, but a DIFFERENT path - which is what keeps subscription
 * callbacks from being delivered to the rent endpoint.
 *
 * POST /api/v1/public/subscription-billing/mpesa/callback/{secret}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/subscription-billing")
@RequiredArgsConstructor
public class SubscriptionPaymentCallbackController {

    private final SubscriptionPaymentCallbackService subscriptionPaymentCallbackService;
    private final DarajaProperties darajaProperties;

    @PostMapping("/mpesa/callback/{secret}")
    public ResponseEntity<Void> mpesaCallback(
            @PathVariable String secret,
            @RequestBody MpesaCallbackPayload payload
    ) {
        String expected = darajaProperties.getCallbackSecret();

        if (expected == null || expected.isBlank()
                || !MessageDigest.isEqual(
                secret.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Subscription payment M-Pesa callback rejected: invalid or missing secret path segment");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        subscriptionPaymentCallbackService.handle(payload);
        return ResponseEntity.ok().build();
    }
}
