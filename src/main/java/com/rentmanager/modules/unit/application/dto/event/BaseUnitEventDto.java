package com.rentmanager.modules.unit.application.dto.event;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseUnitEventDto {

    private final UUID eventId;
    private final UUID unitId;
    private final UUID tenantId;
    private final String correlationId;
    private final Instant occurredAt;

    protected BaseUnitEventDto(
            UUID unitId,
            UUID tenantId,
            String correlationId
    ) {
        this.eventId = UUID.randomUUID();
        this.unitId = unitId;
        this.tenantId = tenantId;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}