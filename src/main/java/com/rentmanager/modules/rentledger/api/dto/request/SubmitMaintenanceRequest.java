package com.rentmanager.modules.rentledger.api.dto.request;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceCategory;
import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitMaintenanceRequest(
        @NotBlank String title,
        String description,
        @NotNull MaintenanceCategory category,
        @NotNull MaintenancePriority priority
) {}
