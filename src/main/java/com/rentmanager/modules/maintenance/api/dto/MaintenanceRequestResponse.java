package com.rentmanager.modules.maintenance.api.dto;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceRequestResponse(
        UUID id,
        UUID unitId,
        UUID propertyId,
        UUID tenantProfileId,
        UUID leaseId,
        String title,
        String description,
        MaintenanceCategory category,
        MaintenancePriority priority,
        MaintenanceRequestStatus status,
        LocalDate scheduledDate,
        Instant completedAt,
        Instant firstLandlordResponseAt,
        Instant landlordViewedAt,
        String notes,
        String createdBy,
        String assignedTo,
        String propertyName,
        String unitNumber,
        String renterName,
        Long version,
        Instant createdAt,
        Instant updatedAt,
        /** Statuses this request may move to next (TD-132). Clients render options from this. */
        java.util.List<MaintenanceRequestStatus> allowedNextStatuses
) {
    /**
     * Raw mapping (enrichment fields null). Used by the query service,
     * which enriches before returning to the controller.
     */
    public static MaintenanceRequestResponse from(MaintenanceRequest request) {
        return from(request, null, null, null);
    }

    public static MaintenanceRequestResponse from(
            MaintenanceRequest request,
            String propertyName,
            String unitNumber,
            String renterName
    ) {
        return new MaintenanceRequestResponse(
                request.getId(),
                request.getUnitId(),
                request.getPropertyId(),
                request.getTenantProfileId(),
                request.getLeaseId(),
                request.getTitle(),
                request.getDescription(),
                request.getCategory(),
                request.getPriority(),
                request.getStatus(),
                request.getScheduledDate(),
                request.getCompletedAt(),
                request.getFirstLandlordResponseAt(),
                request.getLandlordViewedAt(),
                request.getNotes(),
                request.getCreatedBy(),
                request.getAssignedTo(),
                propertyName,
                unitNumber,
                renterName,
                request.getVersion(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getStatus() == null ? java.util.List.of()
                        : request.getStatus().allowedNext().stream().sorted().toList()
        );
    }
}
