package com.rentmanager.modules.tenant.domain.valueobject;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantSettings {

    private final BrandingSettings brandingSettings;

    private final String timezone;

    private final String currency;

    private final String locale;

    // Phase 2b: optional emergency contact rendered on the renter portal
    // only when a phone is actually set. Free tier - not a premium gate.
    private final String emergencyContactPhone;

    private final boolean emergencyContact24h;

    public TenantSettings(
            BrandingSettings brandingSettings,
            String timezone,
            String currency,
            String locale,
            String emergencyContactPhone,
            boolean emergencyContact24h
    ) {
        if (brandingSettings == null) {
            throw new IllegalArgumentException("Branding settings cannot be null");
        }

        this.brandingSettings = brandingSettings;
        this.timezone = timezone;
        this.currency = currency;
        this.locale = locale;
        this.emergencyContactPhone = emergencyContactPhone;
        this.emergencyContact24h = emergencyContact24h;
    }
}
