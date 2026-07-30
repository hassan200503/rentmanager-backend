package com.rentmanager.modules.maintenance.api.dto;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        LocalDateTime completedAt,
        String notes,
        String createdBy,
        String assignedTo,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static MaintenanceRequestResponse from(MaintenanceRequest request) {
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
                request.getNotes(),
                request.getCreatedBy(),
                request.getAssignedTo(),
                request.getVersion(),
                request.getCreatedAt(),
                request.getUpdatedAt()
        );
    }
}
