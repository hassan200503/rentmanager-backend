package com.rentmanager.modules.maintenance.api.dto;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMaintenanceRequest(
        @NotNull UUID unitId,
        @NotNull UUID propertyId,
        @NotNull UUID tenantProfileId,
        UUID leaseId,
        @NotBlank String title,
        String description,
        @NotNull MaintenanceCategory category,
        @NotNull MaintenancePriority priority,
        String createdBy
) {}
