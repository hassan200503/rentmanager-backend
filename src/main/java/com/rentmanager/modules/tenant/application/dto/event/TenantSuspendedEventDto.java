package com.rentmanager.modules.tenant.application.dto.event;

public class TenantSuspendedEventDto extends BaseTenantEventDto {

    private String reason;

    public TenantSuspendedEventDto(
            java.util.UUID tenantId,
            java.util.UUID targetTenantId,
            String reason
    ) {
        super(tenantId, targetTenantId);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}