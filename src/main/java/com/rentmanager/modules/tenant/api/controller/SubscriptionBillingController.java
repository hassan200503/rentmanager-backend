package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.tenant.api.dto.response.RatibaSetupResponse;
import com.rentmanager.modules.tenant.api.dto.response.SubscriptionPaymentRequestResponse;
import com.rentmanager.modules.tenant.application.dto.request.SubscriptionSwitchRequest;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionStatusResponse;
import com.rentmanager.modules.tenant.application.service.SubscriptionBillingService;
import com.rentmanager.shared.security.context.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Landlord-facing subscription billing endpoints (Phase 1 dual revenue
 * model). Mirrors {@code TenantController}'s fail-closed tenant context
 * resolution and OWNER-gating.
 */
@RestController
@RequestMapping("/api/v1/tenants/subscription")
public class SubscriptionBillingController {

    private final SubscriptionBillingService subscriptionBillingService;

    public SubscriptionBillingController(SubscriptionBillingService subscriptionBillingService) {
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @GetMapping
    public ResponseEntity<ApiResponse<SubscriptionStatusResponse>> getStatus() {
        UUID tenantId = resolveStrictTenantId();
        SubscriptionStatusResponse response = subscriptionBillingService.getStatus(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Poll endpoint for the switch STK flow: the frontend polls this with
     * the payment request id returned by /switch until it reaches a
     * terminal status (PAID / FAILED / EXPIRED).
     */
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @GetMapping("/payments/{paymentRequestId}")
    public ResponseEntity<ApiResponse<SubscriptionPaymentRequestResponse>> getPaymentRequestStatus(
            @PathVariable UUID paymentRequestId
    ) {
        UUID tenantId = resolveStrictTenantId();
        SubscriptionPaymentRequestResponse response = SubscriptionPaymentRequestResponse.from(
                subscriptionBillingService.getPaymentRequestStatus(tenantId, paymentRequestId));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Switch COMMISSION -> PREMIUM_MONTHLY. Takes effect only on the first
     * successful subscription payment (STK callback); a failed payment
     * changes nothing.
     */
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @PostMapping("/switch")
    public ResponseEntity<ApiResponse<SubscriptionPaymentRequestResponse>> switchToPremium(
            @Valid @RequestBody SubscriptionSwitchRequest request
    ) {
        UUID tenantId = resolveStrictTenantId();
        SubscriptionPaymentRequestResponse response = SubscriptionPaymentRequestResponse.from(
                subscriptionBillingService.switchToPremium(
                        tenantId, request.getPlanCode(), request.getMpesaPhone()));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Switch PREMIUM_MONTHLY -> COMMISSION. Takes effect at the end of the
     * already-paid period (no clawback); immediate if currently in grace.
     */
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @PostMapping("/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelPremium() {
        UUID tenantId = resolveStrictTenantId();
        subscriptionBillingService.cancelPremium(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Set up M-Pesa Ratiba (standing order) autobilling. Merchant-initiated:
     * the landlord confirms via the NI push on their phone; the response
     * also carries the Paybill / account reference / fee for the manual
     * *334# fallback.
     */
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @PostMapping("/ratiba")
    public ResponseEntity<ApiResponse<RatibaSetupResponse>> setupRatiba() {
        UUID tenantId = resolveStrictTenantId();
        RatibaSetupResponse response = subscriptionBillingService.setupRatibaStandingOrder(tenantId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    private UUID resolveStrictTenantId() {
        UUID tenantId;
        try {
            tenantId = TenantContext.getTenantId();
        } catch (IllegalStateException e) {
            throw new AccessDeniedException(
                    "Tenant context could not be resolved for this request", e);
        }

        if (tenantId == null) {
            throw new AccessDeniedException(
                    "Tenant context could not be resolved for this request");
        }

        return tenantId;
    }
}
