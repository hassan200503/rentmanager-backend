package com.rentmanager.modules.property.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.property.api.routes.PropertyRoutes;
import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import lombok.RequiredArgsConstructor;
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
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestBody CreatePropertyRequest request
    ) {
        return ApiResponse.ok(
                propertyCommandService.createProperty(tenantId, request)
        );
    }

    // ---------------- UPDATE ----------------
    @PutMapping("/{propertyId}")
    public ApiResponse<PropertyResponse> updateProperty(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID propertyId,
            @RequestBody UpdatePropertyRequest request
    ) {
        return ApiResponse.ok(
                propertyCommandService.updateProperty(tenantId, propertyId, request)
        );
    }

    // ---------------- ACTIVATE ----------------
    @PostMapping("/{propertyId}/activate")
    public ApiResponse<PropertyResponse> activateProperty(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID propertyId
    ) {
        return ApiResponse.ok(
                propertyCommandService.activateProperty(tenantId, propertyId)
        );
    }

    // ---------------- ARCHIVE ----------------
    @PostMapping("/{propertyId}/archive")
    public ApiResponse<PropertyResponse> archiveProperty(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID propertyId
    ) {
        return ApiResponse.ok(
                propertyCommandService.archiveProperty(tenantId, propertyId)
        );
    }
}