package com.rentmanager.modules.lease.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a lease is cancelled.
 */
public class LeaseCancelledEventDto extends BaseLeaseEventDto {

    private final UUID leaseId;
    private final String reason;

    public LeaseCancelledEventDto(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            UUID leaseId,
            String reason,
            Instant occurredAt
    ) {
        super(eventId, tenantId, correlationId, actor, occurredAt);

        this.leaseId = leaseId;
        this.reason = reason;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public String getReason() {
        return reason;
    }
}