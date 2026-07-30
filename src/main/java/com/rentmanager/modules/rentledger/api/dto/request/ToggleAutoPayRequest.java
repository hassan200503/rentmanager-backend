package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ToggleAutoPayRequest(
        @NotNull Boolean enabled,
        @NotBlank String mpesaPhone
) {}