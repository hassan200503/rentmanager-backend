package com.rentmanager.modules.property.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.property.api.routes.PropertyRoutes;
import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * RBAC (added this session, per Addendum 2 §4.1 resolution): all four
 * mutating endpoints are gated OWNER+MANAGER. STAFF is deliberately
 * excluded across the whole controller -- unlike Unit's occupancy toggles
 * (markOccupied/markVacant), there is no on-site/operational analog for
 * property create/update/activate/archive; every action here is a
 * structural portfolio decision. This mirrors UserController's tier
 * (hasAnyAuthority ROLE_LANDLORD_OWNER, ROLE_LANDLORD_MANAGER), not
 * TenantController's OWNER-only tier -- property lifecycle actions are
 * reversible/operational in a way TenantController's suspend/Daraja-config
 * actions are not.
 */
@RestController
@RequestMapping(PropertyRoutes.BASE)
@RequiredArgsConstructor
public class PropertyCommandController {

    private final PropertyCommandService propertyCommandService;

    // ---------------- CREATE ----------------
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<PropertyResponse> createProperty(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody CreatePropertyRequest request
    ) {
        try {
            return ApiResponse.ok(
                    propertyCommandService.createProperty(requireTenantId(user), request)
            );
        } catch (DataIntegrityViolationException ex) {
            return ApiResponse.fail("Property constraint violation", "CONFLICT");
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ex.getMessage(), "BAD_REQUEST");
        }
    }

    // ---------------- UPDATE ----------------
    @PutMapping("/{propertyId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<PropertyResponse> updateProperty(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId,
            @RequestBody UpdatePropertyRequest request
    ) {
        try {
            return ApiResponse.ok(
                    propertyCommandService.updateProperty(requireTenantId(user), propertyId, request)
            );
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(ex.getMessage(), "BAD_REQUEST");
        }
    }

    // ---------------- ACTIVATE ----------------
    @PostMapping("/{propertyId}/activate")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<PropertyResponse> activateProperty(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId
    ) {
        return ApiResponse.ok(
                propertyCommandService.activateProperty(requireTenantId(user), propertyId)
        );
    }

    // ---------------- ARCHIVE ----------------
    @PostMapping("/{propertyId}/archive")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<PropertyResponse> archiveProperty(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID propertyId
    ) {
        return ApiResponse.ok(
                propertyCommandService.archiveProperty(requireTenantId(user), propertyId)
        );
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }
}