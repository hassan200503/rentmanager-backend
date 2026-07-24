package com.rentmanager.modules.unit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.unit.api.routes.UnitRoutes;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.shared.security.context.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * SECURITY NOTE (fix applied — see Addendum 3 §1.5):
 * tenantId is NEVER accepted from a client-supplied header on this controller.
 * It is derived exclusively from TenantContext, which is populated server-side
 * by ClerkJwtAuthenticationConverter from the verified Clerk JWT on every
 * authenticated request. Do not reintroduce @RequestHeader("X-Tenant-Id") or
 * any equivalent client-trusted tenant parameter on this controller — doing so
 * previously allowed any authenticated user to read/write any other tenant's
 * unit data by simply setting a header (confirmed cross-tenant IDOR).
 *
 * RBAC (added this session, per Addendum 2 §4.1 resolution):
 * create/update/activate/archive are structural changes to a unit's identity
 * and lifecycle -- gated OWNER+MANAGER, same tier as Property.
 * markOccupied/markVacant are deliberately the exception: this is the
 * on-site caretaker/agent confirming a tenant physically moved in or out --
 * the same "day-to-day operational action performed by whoever is actually
 * on site" reasoning that keeps RentLedger's ordinary rent-recording
 * endpoints open to STAFF. Gating occupancy toggles to MANAGER+ would
 * recreate that exact bottleneck for no security benefit -- an occupancy
 * flag is not money and not structurally destructive.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(UnitRoutes.BASE)
public class UnitCommandController {

    private final UnitCommandService unitCommandService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<UnitResponse>> createUnit(
            @Valid @RequestBody CreateUnitRequest request
    ) {
        UUID tenantId = TenantContext.getTenantId();

        UnitResponse response = unitCommandService.create(tenantId, request);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit created successfully", response)
        );
    }

    @PutMapping("/{unitId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<UnitResponse>> updateUnit(
            @PathVariable UUID unitId,
            @Valid @RequestBody UpdateUnitRequest request
    ) {
        UUID tenantId = TenantContext.getTenantId();

        UnitResponse response = unitCommandService.update(tenantId, unitId, request);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit updated successfully", response)
        );
    }

    @PatchMapping("/{unitId}/activate")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<String>> activateUnit(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID unitId
    ) {
        UUID tenantId = TenantContext.getTenantId();

        unitCommandService.activate(tenantId, unitId, correlationId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit activated successfully", "SUCCESS")
        );
    }

    @PatchMapping("/{unitId}/archive")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<String>> archiveUnit(
            @PathVariable UUID unitId
    ) {
        UUID tenantId = TenantContext.getTenantId();

        unitCommandService.archive(tenantId, unitId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit archived successfully", "SUCCESS")
        );
    }

    @PatchMapping("/{unitId}/occupied")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<String>> markOccupied(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID unitId
    ) {
        UUID tenantId = TenantContext.getTenantId();

        unitCommandService.markOccupied(tenantId, unitId, correlationId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit marked as occupied", "SUCCESS")
        );
    }

    @PatchMapping("/{unitId}/vacant")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<String>> markVacant(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable UUID unitId
    ) {
        UUID tenantId = TenantContext.getTenantId();

        unitCommandService.markVacant(tenantId, unitId, correlationId);

        return ResponseEntity.ok(
                ApiResponse.ok("Unit marked as vacant", "SUCCESS")
        );
    }

    @DeleteMapping("/{unitId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> deleteUnit(
            @PathVariable UUID unitId
    ) {
        UUID tenantId = TenantContext.getTenantId();

        try {
            unitCommandService.delete(tenantId, unitId);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.ok(ApiResponse.fail(ex.getMessage(), "NOT_FOUND"));
        } catch (Exception ex) {
            return ResponseEntity.ok(ApiResponse.fail("Failed to delete unit.", "CONFLICT"));
        }
    }
}