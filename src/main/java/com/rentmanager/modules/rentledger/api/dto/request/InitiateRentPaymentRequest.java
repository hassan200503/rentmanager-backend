package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InitiateRentPaymentRequest(

        @NotBlank
        @Pattern(regexp = "^\\+2547\\d{8}$", message = "Enter a valid M-Pesa number e.g. +254712345678")
        String mpesaPhone
) {}
