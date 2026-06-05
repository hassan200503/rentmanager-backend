package com.rentmanager.modules.lease.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when lease expires naturally (time-based lifecycle end).
 */
public class LeaseExpiredEventDto extends BaseLeaseEventDto {

    private final UUID leaseId;

    public LeaseExpiredEventDto(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            UUID leaseId,
            Instant occurredAt
    ) {
        super(eventId, tenantId, correlationId, actor, occurredAt);

        this.leaseId = leaseId;
    }

    public UUID getLeaseId() {
        return leaseId;
    }
}