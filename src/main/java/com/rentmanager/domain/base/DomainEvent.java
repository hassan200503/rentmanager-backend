package com.rentmanager.domain.base;

import java.time.Instant;
import java.util.UUID;

public abstract class DomainEvent {

    private final UUID eventId;
    private final UUID tenantId;
    private final String correlationId;
    private final Instant occurredAt;
    private final UUID aggregateId;

    protected DomainEvent(UUID tenantId, UUID aggregateId, String correlationId) {
        this.eventId = UUID.randomUUID();
        this.tenantId = tenantId;
        this.aggregateId = aggregateId;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }

    // ===== Required getters =====

    public UUID getEventId() {
        return eventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    /**
     * Must be implemented by every concrete event
     */
    public abstract String eventType();
}