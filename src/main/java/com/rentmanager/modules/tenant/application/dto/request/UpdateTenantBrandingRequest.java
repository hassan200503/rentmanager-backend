package com.rentmanager.modules.tenant.application.dto.request;

import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateTenantBrandingRequest {

    @NotNull(message = "Branding settings are required")
    private BrandingSettings brandingSettings;

    @Size(max = 500, message = "Update reason must not exceed 500 characters")
    private String reason;

    public UpdateTenantBrandingRequest() {
    }

    public BrandingSettings getBrandingSettings() {
        return brandingSettings;
    }

    public void setBrandingSettings(BrandingSettings brandingSettings) {
        this.brandingSettings = brandingSettings;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}