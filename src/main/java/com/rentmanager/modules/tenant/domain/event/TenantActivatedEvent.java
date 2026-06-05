package com.rentmanager.modules.tenant.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class TenantActivatedEvent extends DomainEvent {

    public TenantActivatedEvent(
            UUID tenantId,
            UUID aggregateId,
            String eventType
    ) {
        super(tenantId, aggregateId, eventType);
    }

    @Override
    public String eventType() {
        return "TENANT_ACTIVATED";
    }
}