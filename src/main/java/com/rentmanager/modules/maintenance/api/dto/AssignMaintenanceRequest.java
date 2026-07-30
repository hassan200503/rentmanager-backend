package com.rentmanager.modules.maintenance.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignMaintenanceRequest(
        @NotBlank String assignee
) {}
