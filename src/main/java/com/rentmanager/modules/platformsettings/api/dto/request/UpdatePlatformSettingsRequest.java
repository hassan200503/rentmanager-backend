package com.rentmanager.modules.platformsettings.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePlatformSettingsRequest(
        @NotNull
        @Min(1)
        @Max(60)
        Integer premiumGraceDays,

        @NotNull
        @Min(5)
        @Max(1440)
        Integer subscriptionPaymentExpiryMinutes,

        @NotNull
        @Min(0)
        @Max(10)
        Integer disbursementMaxRetryAttempts,

        @Size(max = 20)
        String revenueBusinessShortcode,

        @Size(max = 20)
        String revenuePaybill,

        @Size(max = 20)
        String revenueTill,

        @Size(max = 20)
        String revenueB2CShortcode,

        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Must be a valid phone number")
        String revenueMpesaPhone,

        @Email
        @Size(max = 150)
        String supportEmail,

        @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Must be a valid phone number")
        String supportPhone
) {
}