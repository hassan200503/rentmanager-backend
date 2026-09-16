package com.rentmanager.modules.rentledger.api.dto.request;

import com.rentmanager.shared.phone.KenyanMsisdn;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record InitiateRentPaymentRequest(

        @NotBlank
        @Pattern(regexp = KenyanMsisdn.PATTERN, message = KenyanMsisdn.MESSAGE)
        String mpesaPhone
) {
    public String normalisedPhone() {
        return KenyanMsisdn.toE164(mpesaPhone);
    }
}
