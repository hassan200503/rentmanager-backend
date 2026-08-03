package com.rentmanager.modules.tenant.application.dto.response;

import com.rentmanager.modules.tenant.domain.valueobject.TenantSettings;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TenantSettingsResponse {

    private String timezone;
    private String currency;
    private String locale;

    private boolean emailNotificationsEnabled;
    private boolean smsNotificationsEnabled;
    private boolean pushNotificationsEnabled;

    private boolean maintenanceModuleEnabled;
    private boolean accountingModuleEnabled;
    private boolean analyticsModuleEnabled;
    private boolean automationModuleEnabled;

    // Branding (Phase 3a): applied to the renter portal theme. The SERVER
    // gates the actual application on PREMIUM_MONTHLY - these fields are
    // returned so the landlord dashboard can edit them, but the renter
    // portal only receives colors via the tenant-portal endpoint when the
    // landlord is premium (see TenantPortalService.getLease).
    private String primaryColor;
    private String secondaryColor;
    private String logoUrl;
    private String faviconUrl;

    // Emergency contact (Phase 2b, free tier)
    private String emergencyContactPhone;
    private boolean emergencyContact24h;

    // KRA tax profile (Phase 1b, free tier)
    private String kraPin;
    private boolean vatRegistered;

    public static TenantSettingsResponse from(TenantSettings settings) {
        return TenantSettingsResponse.builder()
                .timezone(settings.getTimezone())
                .currency(settings.getCurrency())
                .locale(settings.getLocale())
                .primaryColor(settings.getBrandingSettings() != null
                        ? settings.getBrandingSettings().getPrimaryColor() : null)
                .secondaryColor(settings.getBrandingSettings() != null
                        ? settings.getBrandingSettings().getSecondaryColor() : null)
                .logoUrl(settings.getBrandingSettings() != null
                        ? settings.getBrandingSettings().getLogoUrl() : null)
                .faviconUrl(settings.getBrandingSettings() != null
                        ? settings.getBrandingSettings().getFaviconUrl() : null)
                .emergencyContactPhone(settings.getEmergencyContactPhone())
                .emergencyContact24h(settings.isEmergencyContact24h())
                .kraPin(settings.getKraPin())
                .vatRegistered(settings.isVatRegistered())
                .build();
    }
}
