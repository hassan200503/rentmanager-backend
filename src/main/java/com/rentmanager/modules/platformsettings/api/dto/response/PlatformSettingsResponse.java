package com.rentmanager.modules.platformsettings.api.dto.response;

import java.time.Instant;

public record PlatformSettingsResponse(
        BillingSettings billing,
        DisbursementSettings disbursement,
        RevenueSettings revenue,
        PlatformInfo platform
) {

    public record BillingSettings(
            int premiumGraceDays,
            int subscriptionPaymentExpiryMinutes
    ) {
    }

    public record DisbursementSettings(
            int maxRetryAttempts
    ) {
    }

    public record RevenueSettings(
            String businessShortcode,
            String paybill,
            String till,
            String b2cShortcode,
            String mpesaPhone
    ) {
    }

    public record PlatformInfo(
            String environment,
            boolean sandbox,
            String supportEmail,
            String supportPhone,
            String updatedBy,
            Instant updatedAt
    ) {
    }
}