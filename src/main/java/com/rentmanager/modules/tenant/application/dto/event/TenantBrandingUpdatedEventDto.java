package com.rentmanager.modules.tenant.application.dto.event;

import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;

public class TenantBrandingUpdatedEventDto extends BaseTenantEventDto {

    private BrandingSettings brandingSettings;

    public TenantBrandingUpdatedEventDto(
            java.util.UUID tenantId,
            java.util.UUID targetTenantId,
            BrandingSettings brandingSettings
    ) {
        super(tenantId, targetTenantId);
        this.brandingSettings = brandingSettings;
    }

    public BrandingSettings getBrandingSettings() {
        return brandingSettings;
    }
}