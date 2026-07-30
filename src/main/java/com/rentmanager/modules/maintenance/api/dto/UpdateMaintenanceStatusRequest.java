package com.rentmanager.modules.maintenance.api.dto;

import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateMaintenanceStatusRequest(
        @NotNull MaintenanceRequestStatus status
) {}
