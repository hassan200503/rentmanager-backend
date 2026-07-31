package com.rentmanager.modules.tenant.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentmanager.modules.tenant.application.service.RatibaStandingOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Iterator;

/**
 * Receives the async M-Pesa Ratiba creation result callback
 * ({@code createStandingOrderExternal}). The URL is registered per
 * creation request with Daraja (no secret path segment - same trust model
 * as C2B), so the endpoint only acknowledges; all validation is fail-closed
 * in the service (an unknown responseRefID is logged and ignored, never
 * applied).
 *
 * <p>Always returns 200 so Safaricom does not retry an already-delivered
 * callback; failures are recorded on the standing order record instead.</p>
 *
 * POST /api/v1/public/subscription-billing/ratiba/callback
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/subscription-billing/ratiba")
@RequiredArgsConstructor
public class RatibaCallbackController {

    private final RatibaStandingOrderService ratibaStandingOrderService;

    @PostMapping("/callback")
    public ResponseEntity<Void> creationCallback(@RequestBody JsonNode payload) {
        try {
            JsonNode header = firstChild(payload, "responseHeader", "ResponseHeader");
            JsonNode body = firstChild(payload, "responseBody", "ResponseBody");

            String responseRefId = header != null ? header.path("responseRefID").asText(null) : null;
            if (responseRefId == null || responseRefId.isBlank()) {
                log.warn("Ratiba callback missing responseRefID - ignored");
                return ResponseEntity.ok().build();
            }

            boolean successful = isSuccessful(body);
            String transactionId = findValue(body, "TransactionID");
            String failureReason = header != null
                    ? header.path("responseDescription").asText(null)
                    : null;

            ratibaStandingOrderService.handleCreationCallback(
                    responseRefId, successful, transactionId, failureReason);
        } catch (Exception e) {
            log.error("Failed to process Ratiba creation callback", e);
        }
        return ResponseEntity.ok().build();
    }

    private JsonNode firstChild(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    /** ResponseBody.ResponseData is an array of {"name": ..., "value": ...} pairs. */
    private boolean isSuccessful(JsonNode body) {
        String status = findValue(body, "Status");
        return status != null && "OKAY".equalsIgnoreCase(status);
    }

    private String findValue(JsonNode body, String targetName) {
        if (body == null) {
            return null;
        }
        JsonNode data = body.get("responseData");
        if (data == null || !data.isArray()) {
            return null;
        }
        Iterator<JsonNode> elements = data.elements();
        while (elements.hasNext()) {
            JsonNode entry = elements.next();
            String name = entry.path("name").asText(null);
            String value = entry.path("value").asText(null);
            if (targetName.equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }
}
