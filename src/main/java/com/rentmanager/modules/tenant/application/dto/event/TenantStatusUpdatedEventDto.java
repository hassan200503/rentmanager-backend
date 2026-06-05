package com.rentmanager.modules.tenant.application.dto.event;

import com.rentmanager.modules.tenant.domain.enums.TenantStatus;

public class TenantStatusUpdatedEventDto extends BaseTenantEventDto {

    private TenantStatus oldStatus;
    private TenantStatus newStatus;

    public TenantStatusUpdatedEventDto(
            java.util.UUID tenantId,
            java.util.UUID targetTenantId,
            TenantStatus oldStatus,
            TenantStatus newStatus
    ) {
        super(tenantId, targetTenantId);
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public TenantStatus getOldStatus() {
        return oldStatus;
    }

    public TenantStatus getNewStatus() {
        return newStatus;
    }
}