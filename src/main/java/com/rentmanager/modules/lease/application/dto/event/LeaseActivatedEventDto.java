package com.rentmanager.modules.lease.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when lease transitions to ACTIVE state.
 *
 * NOTE:
 * This is a TRANSPORT DTO (not a domain event).
 */
public class LeaseActivatedEventDto extends BaseLeaseEventDto {

    private final UUID leaseId;

    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;

    public LeaseActivatedEventDto(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            UUID leaseId,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            Instant occurredAt
    ) {
        super(eventId, tenantId, correlationId, actor, occurredAt);

        this.leaseId = leaseId;
        this.propertyId = propertyId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
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
}