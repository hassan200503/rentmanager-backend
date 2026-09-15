package com.rentmanager.modules.platformadmin.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminActivateSubscriptionRequest(
        @NotBlank String planCode,
        @NotNull @Min(1) @Max(24) Integer periodMonths
) {}
