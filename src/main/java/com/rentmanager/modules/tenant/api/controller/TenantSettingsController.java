package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.tenant.application.dto.request.UpdateTenantSettingsRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantSettingsResponse;
import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import com.rentmanager.modules.tenant.domain.valueobject.TenantSettings;
import com.rentmanager.modules.tenant.infrastructure.persistence.adapter.TenantSettingsRepositoryAdapter;
import com.rentmanager.shared.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Landlord dashboard settings: branding (Phase 3a) + emergency contact
 * (Phase 2b) + localization. Tenant identity comes ONLY from
 * TenantContext (JWT) - the path tenantId must match, and no client-supplied
 * tenant header is ever trusted.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenants")
public class TenantSettingsController {

    private final TenantSettingsRepositoryAdapter settingsAdapter;

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @GetMapping("/{tenantId}/settings")
    public ResponseEntity<ApiResponse<TenantSettingsResponse>> getSettings(
            @PathVariable UUID tenantId
    ) {
        UUID currentTenant = resolveStrictTenantId();
        requireSameTenant(currentTenant, tenantId);

        TenantSettings settings = settingsAdapter.getSettings(currentTenant);
        return ResponseEntity.ok(ApiResponse.ok(
                "Tenant settings retrieved",
                TenantSettingsResponse.from(settings)));
    }

    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    @PutMapping("/{tenantId}/settings")
    public ResponseEntity<ApiResponse<TenantSettingsResponse>> updateSettings(
            @PathVariable UUID tenantId,
            @RequestBody UpdateTenantSettingsRequest request
    ) {
        UUID currentTenant = resolveStrictTenantId();
        requireSameTenant(currentTenant, tenantId);

        TenantSettings current = settingsAdapter.getSettings(currentTenant);

        BrandingSettings branding = current.getBrandingSettings();
        if (request.getPrimaryColor() != null || request.getSecondaryColor() != null
                || request.getLogoUrl() != null || request.getFaviconUrl() != null) {
            branding = BrandingSettings.create(
                    request.getLogoUrl() != null ? request.getLogoUrl() : branding.getLogoUrl(),
                    request.getFaviconUrl() != null ? request.getFaviconUrl() : branding.getFaviconUrl(),
                    request.getPrimaryColor() != null ? request.getPrimaryColor() : branding.getPrimaryColor(),
                    request.getSecondaryColor() != null ? request.getSecondaryColor() : branding.getSecondaryColor()
            );
        }

        TenantSettings updated = TenantSettings.builder()
                .brandingSettings(branding)
                .timezone(request.getTimezone() != null ? request.getTimezone() : current.getTimezone())
                .currency(request.getCurrency() != null ? request.getCurrency() : current.getCurrency())
                .locale(request.getLocale() != null ? request.getLocale() : current.getLocale())
                .emergencyContactPhone(
                        request.getEmergencyContactPhone() != null
                                ? request.getEmergencyContactPhone()
                                : current.getEmergencyContactPhone())
                .emergencyContact24h(
                        request.getEmergencyContact24h() != null
                                ? request.getEmergencyContact24h()
                                : current.isEmergencyContact24h())
                .build();

        settingsAdapter.updateSettings(currentTenant, updated);

        return ResponseEntity.ok(ApiResponse.ok(
                "Tenant settings updated",
                TenantSettingsResponse.from(updated)));
    }

    private void requireSameTenant(UUID currentTenant, UUID pathTenantId) {
        if (!currentTenant.equals(pathTenantId)) {
            throw new AccessDeniedException(
                    "Path tenant does not match the authenticated tenant");
        }
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
