package com.rentmanager.modules.unit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.unit.api.routes.UnitRoutes;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(UnitRoutes.BASE)
public class UnitCommandController {

    private final UnitCommandService unitCommandService;

    @PostMapping
    public ResponseEntity<ApiResponse<UnitResponse>> createUnit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateUnitRequest request
    ) {

        UnitResponse response = unitCommandService.create(tenantId, request);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit created successfully", response)
        );
    }

    @PutMapping("/{unitId}")
    public ResponseEntity<ApiResponse<UnitResponse>> updateUnit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID unitId,
            @Valid @RequestBody UpdateUnitRequest request
    ) {

        UnitResponse response = unitCommandService.update(tenantId, unitId, request);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit updated successfully", response)
        );
    }

    @PatchMapping("/{unitId}/activate")
    public ResponseEntity<ApiResponse<String>> activateUnit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID unitId
    ) {

        unitCommandService.activate(tenantId, unitId, correlationId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit activated successfully", "SUCCESS")
        );
    }

    @PatchMapping("/{unitId}/archive")
    public ResponseEntity<ApiResponse<String>> archiveUnit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID unitId
    ) {

        unitCommandService.archive(tenantId, unitId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit archived successfully", "SUCCESS")
        );
    }

    @PatchMapping("/{unitId}/occupied")
    public ResponseEntity<ApiResponse<String>> markOccupied(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID unitId
    ) {

        unitCommandService.markOccupied(tenantId, unitId, correlationId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit marked as occupied", "SUCCESS")
        );
    }

    @PatchMapping("/{unitId}/vacant")
    public ResponseEntity<ApiResponse<String>> markVacant(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID unitId
    ) {

        unitCommandService.markVacant(tenantId, unitId, correlationId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit marked as vacant", "SUCCESS")
        );
    }
}