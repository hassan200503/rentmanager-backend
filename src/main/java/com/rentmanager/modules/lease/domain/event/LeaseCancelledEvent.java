package com.rentmanager.modules.lease.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class LeaseCancelledEvent extends DomainEvent {

    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;
    private final String reason;

    public LeaseCancelledEvent(
            UUID tenantId,
            UUID aggregateId,
            String actor,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            String reason
    ) {
        super(tenantId, aggregateId, actor);

        this.propertyId = propertyId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
        this.reason = reason;
    }

    @Override
    public String eventType() {
        return "LEASE_CANCELLED";
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

    public String getReason() {
        return reason;
    }
}