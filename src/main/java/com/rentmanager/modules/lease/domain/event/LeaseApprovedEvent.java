package com.rentmanager.modules.lease.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class LeaseApprovedEvent extends DomainEvent {

    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;

    public LeaseApprovedEvent(
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

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getTenantProfileId() {
        return tenantProfileId;
    }

    @Override
    public String eventType() {
        return "LEASE_APPROVED";
    }
}