package com.rentmanager.modules.tenant.application.dto.event;

public class TenantActivatedEventDto extends BaseTenantEventDto {

    public TenantActivatedEventDto(
            java.util.UUID tenantId,
            java.util.UUID targetTenantId
    ) {
        super(tenantId, targetTenantId);
    }
}