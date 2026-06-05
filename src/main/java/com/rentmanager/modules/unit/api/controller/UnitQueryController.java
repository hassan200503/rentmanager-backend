package com.rentmanager.modules.unit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.unit.api.routes.UnitRoutes;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.query.service.UnitQueryService;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(UnitRoutes.BASE)
public class UnitQueryController {

    private final UnitQueryService unitQueryService;

    @GetMapping("/{unitId}")
    public ResponseEntity<ApiResponse<UnitResponse>> getById(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID unitId
    ) {

        UnitResponse response = unitQueryService.getById(tenantId, unitId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit retrieved successfully", response)
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> getAll(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            Pageable pageable
    ) {

        Page<UnitResponse> response = unitQueryService.getAll(tenantId, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Units retrieved successfully", response)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> search(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam(required = false) String keyword,
            Pageable pageable
    ) {

        Page<UnitResponse> response =
                unitQueryService.search(tenantId, keyword, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit search completed successfully", response)
        );
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> getByProperty(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID propertyId,
            Pageable pageable
    ) {

        Page<UnitResponse> response =
                unitQueryService.getByProperty(tenantId, propertyId, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Property units retrieved successfully", response)
        );
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<Page<UnitResponse>>> getByStatus(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable String status,
            Pageable pageable
    ) {

        UnitStatus unitStatus = UnitStatus.valueOf(status.toUpperCase());

        Page<UnitResponse> response =
                unitQueryService.getByStatus(tenantId, unitStatus, pageable);

        return ResponseEntity.ok(
                ApiResponse.ok("Units retrieved successfully", response)
        );
    }
}