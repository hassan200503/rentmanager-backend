package com.rentmanager.modules.tenant.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class TenantSuspendedEvent extends DomainEvent {

    private final String reason;

    public TenantSuspendedEvent(
            UUID tenantId,
            UUID aggregateId,
            String eventType,
            String reason
    ) {
        super(tenantId, aggregateId, eventType);
        this.reason = reason;
    }

    @Override
    public String eventType() {
        return "TENANT_SUSPENDED";
    }

    public String getReason() {
        return reason;
    }
}