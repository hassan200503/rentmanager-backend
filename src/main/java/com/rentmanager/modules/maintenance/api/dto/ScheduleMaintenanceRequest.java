package com.rentmanager.modules.maintenance.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ScheduleMaintenanceRequest(
        @NotNull LocalDate scheduledDate
) {}
