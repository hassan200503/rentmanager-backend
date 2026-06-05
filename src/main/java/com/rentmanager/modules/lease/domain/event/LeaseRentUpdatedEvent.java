package com.rentmanager.modules.lease.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

public class LeaseRentUpdatedEvent extends DomainEvent {

    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;

    private final BigDecimal oldRent;
    private final BigDecimal newRent;

    public LeaseRentUpdatedEvent(
            UUID tenantId,
            UUID aggregateId,
            String actor,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            BigDecimal oldRent,
            BigDecimal newRent
    ) {
        super(tenantId, aggregateId, actor);

        this.propertyId = propertyId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
        this.oldRent = oldRent;
        this.newRent = newRent;
    }

    @Override
    public String eventType() {
        return "LEASE_RENT_UPDATED";
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

    public BigDecimal getOldRent() {
        return oldRent;
    }

    public BigDecimal getNewRent() {
        return newRent;
    }
}