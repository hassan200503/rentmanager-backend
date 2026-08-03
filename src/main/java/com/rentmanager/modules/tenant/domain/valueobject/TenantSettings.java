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

    // Phase 1b (KRA compliance): the landlord's tax identity. `kraPin` is the
    // KRA PIN of the tenant (landlord organisation); `vatRegistered` is the
    // fail-closed VAT flag that (together with COMMERCIAL premises) decides
    // whether rent is standard-rated 16% VAT. Persisted on the Tenant aggregate.
    private final String kraPin;

    private final boolean vatRegistered;

    public TenantSettings(
            BrandingSettings brandingSettings,
            String timezone,
            String currency,
            String locale,
            String emergencyContactPhone,
            boolean emergencyContact24h,
            String kraPin,
            boolean vatRegistered
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
        this.kraPin = kraPin;
        this.vatRegistered = vatRegistered;
    }
}
