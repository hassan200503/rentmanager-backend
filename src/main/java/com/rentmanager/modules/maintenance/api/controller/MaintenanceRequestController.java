package com.rentmanager.modules.maintenance.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.maintenance.api.dto.AssignMaintenanceRequest;
import com.rentmanager.modules.maintenance.api.dto.CreateMaintenanceRequest;
import com.rentmanager.modules.maintenance.api.dto.MaintenanceRequestResponse;
import com.rentmanager.modules.maintenance.api.dto.ScheduleMaintenanceRequest;
import com.rentmanager.modules.maintenance.api.dto.UpdateMaintenanceStatusRequest;
import com.rentmanager.modules.maintenance.application.dto.MaintenanceSlaSummaryResponse;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestCommandService;
import com.rentmanager.modules.maintenance.application.service.MaintenanceRequestQueryService;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/maintenance")
public class MaintenanceRequestController {

    private final MaintenanceRequestCommandService commandService;
    private final MaintenanceRequestQueryService queryService;

    @PostMapping
    public ResponseEntity<ApiResponse<MaintenanceRequestResponse>> submit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateMaintenanceRequest request
    ) {
        MaintenanceRequest result = commandService.submit(
                requireTenantId(user),
                request.unitId(),
                request.propertyId(),
                request.tenantProfileId(),
                request.leaseId(),
                request.title(),
                request.description(),
                request.category(),
                request.priority(),
                request.createdBy() != null ? request.createdBy() : user.getEmail(),
                UUID.randomUUID().toString()
        );
        return ResponseEntity.ok(ApiResponse.ok("Maintenance request submitted", MaintenanceRequestResponse.from(result)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MaintenanceRequestResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) MaintenanceRequestStatus status,
            @RequestParam(required = false) MaintenancePriority priority,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction
    ) {
        List<MaintenanceRequestResponse> responses = queryService.getRequests(
                requireTenantId(user), status, priority, sort, direction);
        return ResponseEntity.ok(ApiResponse.ok("Maintenance requests retrieved", responses));
    }

    @GetMapping("/sla")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<MaintenanceSlaSummaryResponse>> sla(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        MaintenanceSlaSummaryResponse summary = queryService.getSlaSummary(requireTenantId(user));
        return ResponseEntity.ok(ApiResponse.ok("Maintenance SLA summary retrieved", summary));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MaintenanceRequestResponse>> get(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        MaintenanceRequest result = queryService.findByIdAndTenantId(id, requireTenantId(user));
        return ResponseEntity.ok(ApiResponse.ok("Maintenance request retrieved", MaintenanceRequestResponse.from(result)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<MaintenanceRequestResponse>> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMaintenanceStatusRequest request
    ) {
        MaintenanceRequest result = commandService.updateStatus(
                requireTenantId(user), id, request.status(), UUID.randomUUID().toString());
        return ResponseEntity.ok(ApiResponse.ok("Status updated", MaintenanceRequestResponse.from(result)));
    }

    @PatchMapping("/{id}/schedule")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<MaintenanceRequestResponse>> schedule(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody ScheduleMaintenanceRequest request
    ) {
        MaintenanceRequest result = commandService.schedule(
                requireTenantId(user), id, request.scheduledDate(), UUID.randomUUID().toString());
        return ResponseEntity.ok(ApiResponse.ok("Maintenance scheduled", MaintenanceRequestResponse.from(result)));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<MaintenanceRequestResponse>> assign(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @Valid @RequestBody AssignMaintenanceRequest request
    ) {
        MaintenanceRequest result = commandService.assign(
                requireTenantId(user), id, request.assignee(), UUID.randomUUID().toString());
        return ResponseEntity.ok(ApiResponse.ok("Maintenance assigned", MaintenanceRequestResponse.from(result)));
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user");
        }
        return tenantId;
    }
}
