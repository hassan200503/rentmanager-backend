package com.rentmanager.modules.maintenance.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestStatusChanged;
import com.rentmanager.modules.maintenance.domain.events.MaintenanceRequestSubmitted;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MaintenanceRequest extends AggregateRoot {

    private UUID unitId;
    private UUID propertyId;
    private UUID tenantProfileId;
    private UUID leaseId;
    private String title;
    private String description;
    private MaintenanceCategory category;
    private MaintenancePriority priority;
    private MaintenanceRequestStatus status;
    private LocalDate scheduledDate;
    private LocalDateTime completedAt;
    private String notes;
    private String createdBy;
    private String assignedTo;

    public static MaintenanceRequest submit(
            UUID tenantId,
            UUID unitId,
            UUID propertyId,
            UUID tenantProfileId,
            UUID leaseId,
            String title,
            String description,
            MaintenanceCategory category,
            MaintenancePriority priority,
            String createdBy,
            String correlationId
    ) {
        MaintenanceRequest request = MaintenanceRequest.builder()
                .unitId(unitId)
                .propertyId(propertyId)
                .tenantProfileId(tenantProfileId)
                .leaseId(leaseId)
                .title(title)
                .description(description)
                .category(category)
                .priority(priority)
                .status(MaintenanceRequestStatus.SUBMITTED)
                .createdBy(createdBy)
                .build();

        request.assignTenant(tenantId);

        request.registerEvent(new MaintenanceRequestSubmitted(
                tenantId, request.getId(), correlationId,
                unitId, propertyId, tenantProfileId, leaseId,
                title, category, priority
        ));

        return request;
    }

    public void changeStatus(MaintenanceRequestStatus newStatus, String correlationId) {
        MaintenanceRequestStatus oldStatus = this.status;
        this.status = newStatus;

        if (newStatus == MaintenanceRequestStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
        }

        registerEvent(new MaintenanceRequestStatusChanged(
                getTenantId(), getId(), correlationId,
                tenantProfileId, oldStatus, newStatus
        ));
    }

    public void schedule(LocalDate date, String correlationId) {
        this.scheduledDate = date;
        changeStatus(MaintenanceRequestStatus.SCHEDULED, correlationId);
    }

    public void assignTo(String assignee, String correlationId) {
        this.assignedTo = assignee;
        if (this.status == MaintenanceRequestStatus.SUBMITTED) {
            changeStatus(MaintenanceRequestStatus.IN_REVIEW, correlationId);
        }
    }

    public static MaintenanceRequest rehydrate(
            UUID id,
            UUID tenantId,
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
        MaintenanceRequest request = MaintenanceRequest.builder()
                .unitId(unitId)
                .propertyId(propertyId)
                .tenantProfileId(tenantProfileId)
                .leaseId(leaseId)
                .title(title)
                .description(description)
                .category(category)
                .priority(priority)
                .status(status)
                .scheduledDate(scheduledDate)
                .completedAt(completedAt)
                .notes(notes)
                .createdBy(createdBy)
                .assignedTo(assignedTo)
                .build();

        request.setId(id);
        request.assignTenant(tenantId);
        request.setVersion(version);
        request.restoreCreatedAt(createdAt);
        request.restoreUpdatedAt(updatedAt);

        return request;
    }
}
