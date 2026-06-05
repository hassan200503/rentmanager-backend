package com.rentmanager.modules.lease.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a lease is successfully created.
 */
public class LeaseCreatedEventDto extends BaseLeaseEventDto {

    private final UUID leaseId;
    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;

    private final Instant startDate;
    private final Instant endDate;

    public LeaseCreatedEventDto(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            UUID leaseId,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            Instant startDate,
            Instant endDate,
            Instant occurredAt
    ) {
        super(eventId, tenantId, correlationId, actor, occurredAt);

        this.leaseId = leaseId;
        this.propertyId = propertyId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public UUID getLeaseId() {
        return leaseId;
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

    public Instant getStartDate() {
        return startDate;
    }

    public Instant getEndDate() {
        return endDate;
    }
}