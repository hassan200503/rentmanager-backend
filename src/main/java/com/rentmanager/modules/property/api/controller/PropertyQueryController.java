package com.rentmanager.modules.property.api.controller;

import com.rentmanager.modules.property.api.routes.PropertyRoutes;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.property.application.query.service.PropertyQueryService;
import com.rentmanager.contract.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(PropertyRoutes.BASE)
public class PropertyQueryController {

    private final PropertyQueryService propertyQueryService;

    // =========================================================
    // GET BY ID (TENANT SAFE)
    // =========================================================
    @GetMapping("/{propertyId}")
    public ResponseEntity<ApiResponse<PropertyResponse>> getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID propertyId
    ) {

        PropertyResponse response =
                propertyQueryService.getById(tenantId, propertyId);

        return ResponseEntity.ok(
                ApiResponse.ok("Property retrieved successfully", response)
        );
    }

    // =========================================================
    // GET ALL (TENANT SAFE)
    // =========================================================
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PropertyResponse>>> getAll(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            Pageable pageable
    ) {

        Page<PropertyResponse> response =
                propertyQueryService.getAll(tenantId, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Properties retrieved successfully", response)
        );
    }

    // =========================================================
    // SEARCH (TENANT SAFE)
    // =========================================================
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<PropertyResponse>>> search(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {

        Page<PropertyResponse> response =
                propertyQueryService.search(tenantId, keyword, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Property search completed successfully", response)
        );
    }

    // =========================================================
    // BY OWNER (TENANT SAFE)
    // =========================================================
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> getByOwner(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID ownerId
    ) {

        List<PropertyResponse> response =
                propertyQueryService.getByOwner(tenantId, ownerId);

        return ResponseEntity.ok(
                ApiResponse.ok("Owner properties retrieved successfully", response)
        );
    }

    // =========================================================
    // BY STATUS (TENANT SAFE)
    // =========================================================
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<List<PropertyResponse>>> getByStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String status
    ) {

        List<PropertyResponse> response =
                propertyQueryService.getByStatus(tenantId, status);

        return ResponseEntity.ok(
                ApiResponse.ok("Properties retrieved successfully", response)
        );
    }
}