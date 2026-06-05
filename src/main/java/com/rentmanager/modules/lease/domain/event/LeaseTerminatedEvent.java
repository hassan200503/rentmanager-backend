package com.rentmanager.modules.lease.domain.event;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.lease.domain.enums.TerminationType;

import java.util.UUID;

public class LeaseTerminatedEvent extends DomainEvent {

    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;

    private final TerminationType terminationType;
    private final String terminationReason;

    public LeaseTerminatedEvent(
            UUID tenantId,
            UUID aggregateId,
            String actor,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            TerminationType terminationType,
            String terminationReason
    ) {
        super(tenantId, aggregateId, actor);

        this.propertyId = propertyId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;

        this.terminationType = terminationType;
        this.terminationReason = terminationReason;
    }

    @Override
    public String eventType() {
        return "LEASE_TERMINATED";
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

    public TerminationType getTerminationType() {
        return terminationType;
    }

    public String getTerminationReason() {
        return terminationReason;
    }
}