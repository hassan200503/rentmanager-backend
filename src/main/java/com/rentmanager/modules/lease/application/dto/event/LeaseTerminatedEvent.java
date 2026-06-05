package com.rentmanager.modules.lease.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when lease is terminated (end of lifecycle).
 *
 * SaaS-grade event:
 * - tenant-safe
 * - traceable (correlationId + actor)
 * - immutable
 */
public final class LeaseTerminatedEvent extends BaseLeaseEventDto {

    private final UUID leaseId;
    private final String reason;

    public LeaseTerminatedEvent(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            Instant occurredAt,
            UUID leaseId,
            String reason
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