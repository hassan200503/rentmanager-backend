package com.rentmanager.modules.tenant.renter.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class TenantProfileCreatedEvent extends DomainEvent {

    private final UUID tenantProfileId;

    public TenantProfileCreatedEvent(UUID tenantId, UUID aggregateId, String correlationId, UUID tenantProfileId) {
        super(tenantId, aggregateId, correlationId);
        this.tenantProfileId = tenantProfileId;
    }

    public UUID getTenantProfileId() {
        return tenantProfileId;
    }

    @Override
    public String eventType() {
        return "TENANT_PROFILE_CREATED";
    }
}