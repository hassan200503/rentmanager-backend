package com.rentmanager.modules.tenant.application.dto.request;

import lombok.Data;

@Data
public class UpdateTenantSettingsRequest {

    private String timezone;
    private String currency;
    private String locale;

    // REMOVED: the three notification booleans. They were accepted, ignored,
    // and had no column behind them. Accepting a field and discarding it is
    // worse than rejecting it — the caller believes the setting saved.

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

    // KRA tax profile (Phase 1b, free tier) — landlord KRA PIN and the
    // fail-closed VAT-registration flag. `vatRegistered` reaching the tenant
    // aggregate is what unlocks the 16% VAT branch for COMMERCIAL premises.
    private String kraPin;
    private Boolean vatRegistered;
}
