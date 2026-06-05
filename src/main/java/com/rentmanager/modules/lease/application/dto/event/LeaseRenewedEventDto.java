package com.rentmanager.modules.lease.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a lease is renewed.
 */
public class LeaseRenewedEventDto extends BaseLeaseEventDto {

    private final UUID leaseId;
    private final Instant newEndDate;

    public LeaseRenewedEventDto(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            UUID leaseId,
            Instant newEndDate,
            Instant occurredAt
    ) {
        super(eventId, tenantId, correlationId, actor, occurredAt);

        this.leaseId = leaseId;
        this.newEndDate = newEndDate;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public Instant getNewEndDate() {
        return newEndDate;
    }
}