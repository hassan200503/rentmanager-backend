package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.tenant.application.command.service.TenantCommandService;
import com.rentmanager.modules.tenant.application.dto.request.ConfigureDarajaCredentialsRequest;
import com.rentmanager.modules.tenant.application.dto.request.CreateTenantRequest;
import com.rentmanager.modules.tenant.application.dto.request.SuspendTenantRequest;
import com.rentmanager.modules.tenant.application.dto.request.UpdateTenantRequest;
import com.rentmanager.modules.tenant.application.dto.response.DarajaCredentialsStatusResponse;
import com.rentmanager.modules.tenant.application.dto.response.DarajaCredentialsTestResponse;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import com.rentmanager.shared.security.context.TenantContext;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController

@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantCommandService tenantCommandService;

    public TenantController(TenantCommandService tenantCommandService) {
        this.tenantCommandService = tenantCommandService;
    }

    // ------------------------------------------------------------
    // CREATE TENANT (SaaS entry point)
    // ------------------------------------------------------------
    @PostMapping
    public ResponseEntity<ApiResponse<TenantResponse>> createTenant(
            @RequestBody CreateTenantRequest request
    ) {

        UUID tenantId = resolveStrictTenantId();

        TenantResponse response =
                tenantCommandService.createTenant(tenantId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ------------------------------------------------------------
    // GET TENANT
    // ------------------------------------------------------------
    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<TenantResponse>> getTenant(
            @PathVariable UUID tenantId
    ) {

        UUID currentTenant = resolveStrictTenantId();

        TenantResponse response =
                tenantCommandService.getTenant(currentTenant, tenantId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ------------------------------------------------------------
    // UPDATE TENANT PROFILE (name, contact phone, email, address)
    // ------------------------------------------------------------
    // Self-service: a landlord updates their own display name, phone, and
    // address as shown to renters on the tenant portal. OWNER-only — staff
    // can read but should not rewrite the landlord identity.
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @PatchMapping("/{tenantId}/profile")
    public ResponseEntity<ApiResponse<TenantResponse>> updateTenantProfile(
            @PathVariable UUID tenantId,
            @RequestBody UpdateTenantRequest request
    ) {
        UUID currentTenant = resolveStrictTenantId();
        TenantResponse response = tenantCommandService.updateTenantProfile(currentTenant, tenantId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ------------------------------------------------------------
    // GET DARAJA CREDENTIALS STATUS (NEW — additive only)
    // ------------------------------------------------------------
    // Read-only companion to configureDarajaCredentials() below. Returns
    // only the boolean `configured` flag — never any credential material,
    // consistent with DarajaCredentialsStatusResponse's existing "deliberately
    // minimal" contract used on the PUT response. OWNER-gated to match the
    // rest of this controller's Daraja/lifecycle actions; if the product
    // decision is that MANAGER/STAFF should also be able to see whether
    // Daraja is configured (without editing it), this @PreAuthorize should
    // be relaxed — that's a product call, not made here.
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @GetMapping("/{tenantId}/daraja-credentials/status")
    public ResponseEntity<ApiResponse<DarajaCredentialsStatusResponse>> getDarajaCredentialsStatus(
            @PathVariable UUID tenantId
    ) {
        UUID currentTenant = resolveStrictTenantId();

        DarajaCredentialsStatusResponse response =
                tenantCommandService.getDarajaCredentialsStatus(currentTenant, tenantId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ------------------------------------------------------------
    // CONFIGURE DARAJA (M-PESA) CREDENTIALS
    // ------------------------------------------------------------
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @PutMapping("/{tenantId}/daraja-credentials")
    public ResponseEntity<ApiResponse<DarajaCredentialsStatusResponse>> configureDarajaCredentials(
            @PathVariable UUID tenantId,
            @Valid @RequestBody ConfigureDarajaCredentialsRequest request
    ) {
        UUID currentTenant = resolveStrictTenantId();

        DarajaCredentialsStatusResponse response =
                tenantCommandService.configureDarajaCredentials(currentTenant, tenantId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ------------------------------------------------------------
    // TEST DARAJA (M-PESA) CREDENTIALS
    // ------------------------------------------------------------
    // Real call to Safaricom with the SAVED credentials — no mock, no
    // "looks well-formed" check. OWNER-gated to match the configure action:
    // the test reveals whether a credential works, which is information
    // about the credential itself.
    //
    // POST rather than GET because it causes an outbound call to a third
    // party; it must not be retried by a cache or a link prefetch.
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @PostMapping("/{tenantId}/daraja-credentials/test")
    public ResponseEntity<ApiResponse<DarajaCredentialsTestResponse>> testDarajaCredentials(
            @PathVariable UUID tenantId
    ) {
        UUID currentTenant = resolveStrictTenantId();

        DarajaCredentialsTestResponse response =
                tenantCommandService.testDarajaCredentials(currentTenant, tenantId);

        // 200 either way: the request succeeded, and the body carries
        // Safaricom's verdict. A 4xx here would conflate "we could not run
        // the test" with "Safaricom rejected the key".
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ------------------------------------------------------------
    // SUSPEND TENANT
    // ------------------------------------------------------------
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @PutMapping("/{tenantId}/suspend")
    public ResponseEntity<ApiResponse<TenantResponse>> suspendTenant(
            @PathVariable UUID tenantId,
            @Valid @RequestBody SuspendTenantRequest request
    ) {
        UUID currentTenant = resolveStrictTenantId();

        TenantResponse response =
                tenantCommandService.suspendTenant(currentTenant, tenantId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // ------------------------------------------------------------
    // ACTIVATE TENANT
    // ------------------------------------------------------------
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @PutMapping("/{tenantId}/activate")
    public ResponseEntity<ApiResponse<TenantResponse>> activateTenant(
            @PathVariable UUID tenantId
    ) {
        UUID currentTenant = resolveStrictTenantId();

        TenantResponse response =
                tenantCommandService.activateTenant(currentTenant, tenantId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Fail-closed tenant context resolution used by all endpoints in this
     * controller. An unresolvable tenant context results in a rejected
     * request (403 via GlobalExceptionHandler's AccessDeniedException
     * mapping), never a silently-applied fallback identity.
     */
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