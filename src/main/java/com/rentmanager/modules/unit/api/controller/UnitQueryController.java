package com.rentmanager.modules.unit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.unit.api.routes.UnitRoutes;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.dto.response.UnitSummaryResponse;
import com.rentmanager.modules.unit.application.query.service.UnitQueryService;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.shared.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * SECURITY NOTE (fix applied — see Addendum 3 §1.5):
 * tenantId is NEVER accepted from a client-supplied header on this controller.
 * It is derived exclusively from TenantContext, which is populated server-side
 * by ClerkJwtAuthenticationConverter from the verified Clerk JWT on every
 * authenticated request. Do not reintroduce @RequestHeader("X-Tenant-Id") or
 * any equivalent client-trusted tenant parameter on this controller — doing so
 * previously allowed any authenticated user to read any other tenant's unit
 * data by simply setting a header (confirmed cross-tenant IDOR).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(UnitRoutes.BASE)
public class UnitQueryController {

    private final UnitQueryService unitQueryService;

    @GetMapping("/{unitId}")
    public ResponseEntity<ApiResponse<UnitResponse>> getById(
            @PathVariable UUID unitId
    ) {
        UUID tenantId = TenantContext.getTenantId();

        UnitResponse response = unitQueryService.getById(tenantId, unitId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit retrieved successfully", response)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> getAll(
            Pageable pageable
    ) {
        UUID tenantId = TenantContext.getTenantId();

        Page<UnitResponse> response = unitQueryService.getAll(tenantId, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Units retrieved successfully", response)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> search(
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {
        UUID tenantId = TenantContext.getTenantId();

        Page<UnitResponse> response =
                unitQueryService.search(tenantId, keyword, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit search completed successfully", response)
        );
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> getByProperty(
            @PathVariable UUID propertyId,
            Pageable pageable
    ) {
        UUID tenantId = TenantContext.getTenantId();

        Page<UnitResponse> response =
                unitQueryService.getByProperty(tenantId, propertyId, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Property units retrieved successfully", response)
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> getByStatus(
            @PathVariable String status,
            Pageable pageable
    ) {
        UUID tenantId = TenantContext.getTenantId();

        UnitStatus unitStatus = UnitStatus.valueOf(status.toUpperCase());

        Page<UnitResponse> response =
                unitQueryService.getByStatus(tenantId, unitStatus, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Units retrieved successfully", response)
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<UnitSummaryResponse>> getSummary() {
        UUID tenantId = TenantContext.getTenantId();

        UnitSummaryResponse response = unitQueryService.getSummary(tenantId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit summary retrieved successfully", response)
        );
    }
}