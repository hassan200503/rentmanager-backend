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

    public TenantSettings(
            BrandingSettings brandingSettings,
            String timezone,
            String currency,
            String locale
    ) {
        if (brandingSettings == null) {
            throw new IllegalArgumentException("Branding settings cannot be null");
        }

        this.brandingSettings = brandingSettings;
        this.timezone = timezone;
        this.currency = currency;
        this.locale = locale;
    }
}