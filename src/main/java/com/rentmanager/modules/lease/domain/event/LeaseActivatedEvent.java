package com.rentmanager.modules.lease.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class LeaseActivatedEvent extends DomainEvent {

    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;

    public LeaseActivatedEvent(
            UUID tenantId,
            UUID aggregateId,
            String actor,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId
    ) {
        super(tenantId, aggregateId, actor);

        this.propertyId = propertyId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
    }

    @Override
    public String eventType() {
        return "LEASE_ACTIVATED";
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getTenantProfileId() {
        return tenantProfileId;
    }
}