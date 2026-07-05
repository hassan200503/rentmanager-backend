package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.tenant.application.command.service.TenantCommandService;
import com.rentmanager.modules.tenant.application.dto.request.ConfigureDarajaCredentialsRequest;
import com.rentmanager.modules.tenant.application.dto.request.CreateTenantRequest;
import com.rentmanager.modules.tenant.application.dto.request.SuspendTenantRequest;
import com.rentmanager.modules.tenant.application.dto.response.DarajaCredentialsStatusResponse;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import com.rentmanager.shared.security.context.TenantContext;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantCommandService tenantCommandService;

    public TenantController(TenantCommandService tenantCommandService) {
        this.tenantCommandService = tenantCommandService;
    }

    // ------------------------------------------------------------
    // CREATE TENANT (SaaS entry point)
    // ------------------------------------------------------------
    // Changed from resolveTenantId() to resolveStrictTenantId() — see
    // Addendum 5 §1. Frontend/backend audit found no legitimate caller
    // that depends on the fallback-to-default-tenant behavior (no
    // frontend code calls this endpoint outside generated OpenAPI type
    // stubs; no Clerk org-creation webhook exists in this backend).
    // An unresolvable tenant context must now be rejected outright,
    // never silently collapsed onto a shared fallback UUID.
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
    // Changed from resolveTenantId() to resolveStrictTenantId() — same
    // reasoning as createTenant() above. Previously, two different callers
    // both hitting the fallback would collapse onto the same identity,
    // letting one pass validateTenantAccess() against the other's record.
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
     * Fail-closed tenant context resolution used by all five endpoints
     * in this controller. An unresolvable tenant context results in a
     * rejected request (403 via GlobalExceptionHandler's
     * AccessDeniedException mapping), never a silently-applied fallback
     * identity.
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