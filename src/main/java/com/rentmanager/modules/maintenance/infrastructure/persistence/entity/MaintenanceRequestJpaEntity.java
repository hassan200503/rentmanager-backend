package com.rentmanager.modules.maintenance.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseTenantEntity;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "maintenance_requests")
public class MaintenanceRequestJpaEntity extends BaseTenantEntity {

    @Column(name = "unit_id", nullable = false)
    private java.util.UUID unitId;

    @Column(name = "property_id", nullable = false)
    private java.util.UUID propertyId;

    @Column(name = "tenant_profile_id", nullable = false)
    private java.util.UUID tenantProfileId;

    @Column(name = "lease_id")
    private java.util.UUID leaseId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private MaintenanceCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 50)
    private MaintenancePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private MaintenanceRequestStatus status;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "first_landlord_response_at")
    private LocalDateTime firstLandlordResponseAt;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;
}
