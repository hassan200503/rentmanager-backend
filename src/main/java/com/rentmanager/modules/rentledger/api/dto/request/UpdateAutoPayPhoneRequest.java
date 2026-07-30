package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateAutoPayPhoneRequest(
        @NotBlank String mpesaPhone
) {}