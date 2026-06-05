package com.rentmanager.modules.lease.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base contract for all Lease application events.
 * SaaS-grade: immutable, tenant-safe, traceable.
 */
public abstract class BaseLeaseEventDto {

    private final UUID eventId;
    private final UUID tenantId;
    private final String correlationId;
    private final String actor;
    private final Instant occurredAt;

    protected BaseLeaseEventDto(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            Instant occurredAt
    ) {
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.correlationId = correlationId;
        this.actor = actor;
        this.occurredAt = occurredAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getActor() {
        return actor;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}