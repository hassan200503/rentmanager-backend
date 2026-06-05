package com.rentmanager.modules.tenant.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class TenantCreatedEvent extends DomainEvent {

    private final String name;
    private final String email;

    public TenantCreatedEvent(
            UUID tenantId,
            UUID aggregateId,
            String eventType,
            String name,
            String email
    ) {
        super(tenantId, aggregateId, eventType);
        this.name = name;
        this.email = email;
    }

    @Override
    public String eventType() {
        return "TENANT_CREATED";
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }
}