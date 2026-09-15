package com.rentmanager.modules.rentledger.api.dto.request;

import com.rentmanager.shared.phone.KenyanMsisdn;
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
        @Pattern(regexp = KenyanMsisdn.PATTERN, message = KenyanMsisdn.MESSAGE)
        String mpesaPhone
) {
    public String normalisedPhone() {
        return KenyanMsisdn.toE164(mpesaPhone);
    }
}
