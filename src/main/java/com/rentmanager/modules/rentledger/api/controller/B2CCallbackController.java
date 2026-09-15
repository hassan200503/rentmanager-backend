package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.modules.rentledger.application.service.B2CDisbursementService;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

/**
 * Public callback endpoints for Daraja B2C ResultURL and QueueTimeOutURL.
 *
 * The disbursement ID is embedded in the URL path so that Safaricom's
 * callback (which carries only M-Pesa fields) can be matched back to our
 * internal record. Both endpoints are gated by a shared secret path
 * segment, mirroring the existing STK push callback security pattern.
 *
 * URL pattern:
 *   /api/v1/public/disbursements/mpesa/result/{secret}/{disbursementId}
 *   /api/v1/public/disbursements/mpesa/timeout/{secret}/{disbursementId}
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/disbursements/mpesa")
@RequiredArgsConstructor
public class B2CCallbackController {

    private final B2CDisbursementService b2cDisbursementService;
    private final DarajaB2CProperties b2cProperties;

    @PostMapping("/result/{secret}/{disbursementId}")
    public ResponseEntity<Void> handleResult(
            @PathVariable String secret,
            @PathVariable UUID disbursementId,
            @RequestBody Map<String, Object> payload
    ) {
        if (!constantTimeEquals(secret, b2cProperties.getCallbackSecret())) {
            log.warn("B2C result callback received with invalid secret");
            return ResponseEntity.notFound().build();
        }

        log.info("B2C result callback for disbursementId={}", disbursementId);

        try {
            String resultCode = extractString(payload, "ResultCode", "1");
            String resultDesc = extractString(payload, "ResultDesc", "Unknown error");
            String transactionId = extractTransactionId(payload);
            String conversationId = extractString(payload, "ConversationID", null);

            b2cDisbursementService.handleResult(disbursementId, resultCode, resultDesc, transactionId, conversationId);
        } catch (Exception e) {
            log.error("Failed to process B2C result callback for disbursementId={}", disbursementId, e);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/timeout/{secret}/{disbursementId}")
    public ResponseEntity<Void> handleTimeout(
            @PathVariable String secret,
            @PathVariable UUID disbursementId,
            @RequestBody Map<String, Object> payload
    ) {
        if (!constantTimeEquals(secret, b2cProperties.getCallbackSecret())) {
            log.warn("B2C timeout callback received with invalid secret");
            return ResponseEntity.notFound().build();
        }

        log.info("B2C timeout callback for disbursementId={}", disbursementId);

        try {
            b2cDisbursementService.handleTimeout(disbursementId);
        } catch (Exception e) {
            log.error("Failed to process B2C timeout callback for disbursementId={}", disbursementId, e);
        }

        return ResponseEntity.ok().build();
    }

    private String extractString(Map<String, Object> payload, String key, String fallback) {
        Object val = payload.get(key);
        return val != null ? val.toString() : fallback;
    }

    private String extractTransactionId(Map<String, Object> payload) {
        Map<String, Object> resultParams = (Map<String, Object>) payload.get("ResultParameters");
        if (resultParams != null) {
            Object txnId = resultParams.get("TransactionID");
            if (txnId != null) return txnId.toString();
        }
        Object txnId = payload.get("TransactionID");
        return txnId != null ? txnId.toString() : null;
    }

    private boolean constantTimeEquals(String a, String b) {
        // A blank configured secret must never match (unconfigured environment).
        if (a == null || b == null || b.isBlank()) return false;
        return MessageDigest.isEqual(a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}