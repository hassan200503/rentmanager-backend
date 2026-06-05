package com.rentmanager.modules.tenant.application.dto.event;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;

public class TenantSubscriptionUpdatedEventDto extends BaseTenantEventDto {

    private SubscriptionStatus oldStatus;
    private SubscriptionStatus newStatus;

    public TenantSubscriptionUpdatedEventDto(
            java.util.UUID tenantId,
            java.util.UUID targetTenantId,
            SubscriptionStatus oldStatus,
            SubscriptionStatus newStatus
    ) {
        super(tenantId, targetTenantId);
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }

    public SubscriptionStatus getOldStatus() {
        return oldStatus;
    }

    public SubscriptionStatus getNewStatus() {
        return newStatus;
    }
}