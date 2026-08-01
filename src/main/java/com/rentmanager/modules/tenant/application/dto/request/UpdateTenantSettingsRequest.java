package com.rentmanager.modules.tenant.application.dto.request;

import lombok.Data;

@Data
public class UpdateTenantSettingsRequest {

    private String timezone;
    private String currency;
    private String locale;

    private Boolean emailNotificationsEnabled;
    private Boolean smsNotificationsEnabled;
    private Boolean pushNotificationsEnabled;

    private Boolean maintenanceModuleEnabled;
    private Boolean accountingModuleEnabled;
    private Boolean analyticsModuleEnabled;
    private Boolean automationModuleEnabled;

    private Boolean allowCustomBranding;
    private Boolean allowApiAccess;

    // Branding (Phase 3a) - applied to the renter portal theme, server-gated
    // on PREMIUM_MONTHLY. Editable by the landlord at any time; the gate
    // lives on the read path (TenantPortalService.getLease), not here.
    private String primaryColor;
    private String secondaryColor;
    private String logoUrl;
    private String faviconUrl;

    // Emergency contact (Phase 2b, free tier)
    private String emergencyContactPhone;
    private Boolean emergencyContact24h;
}
