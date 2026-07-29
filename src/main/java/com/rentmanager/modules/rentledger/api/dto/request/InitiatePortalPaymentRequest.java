package com.rentmanager.modules.rentledger.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record InitiatePortalPaymentRequest(

        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        BigDecimal amount,

        @NotBlank
        @Pattern(
                regexp = "^(?:\\+2547\\d{8}|07\\d{8}|2547\\d{8})$",
                message = "Enter a valid M-Pesa number: 0712345678, +254712345678, or 254712345678"
        )
        String mpesaPhone
) {
    public String normalisedPhone() {
        String raw = mpesaPhone.strip();
        if (raw.startsWith("07")) {
            return "+254" + raw.substring(1);
        }
        if (raw.startsWith("254")) {
            return "+" + raw;
        }
        return raw;
    }
}
