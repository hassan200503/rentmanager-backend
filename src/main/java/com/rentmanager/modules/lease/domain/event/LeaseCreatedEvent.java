package com.rentmanager.modules.lease.domain.event;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;

import java.util.UUID;

public class LeaseCreatedEvent extends DomainEvent {

    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;
    private final LeaseStatus status;

    public LeaseCreatedEvent(
            UUID tenantId,
            UUID leaseId,
            String actor,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            LeaseStatus status
    ) {
        super(tenantId, leaseId, actor);

        this.propertyId = propertyId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
        this.status = status;
    }

    @Override
    public String eventType() {
        return "LEASE_CREATED";
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

    public LeaseStatus getStatus() {
        return status;
    }
}