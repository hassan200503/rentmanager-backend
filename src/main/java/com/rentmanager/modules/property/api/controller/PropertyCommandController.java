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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(PropertyRoutes.BASE)
@RequiredArgsConstructor
public class PropertyCommandController {

    private final PropertyCommandService propertyCommandService;

    // ---------------- CREATE ----------------
    @PostMapping
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