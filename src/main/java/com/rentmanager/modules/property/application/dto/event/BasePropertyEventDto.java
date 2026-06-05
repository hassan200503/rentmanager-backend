package com.rentmanager.modules.property.application.dto.event;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class BasePropertyEventDto {

    private final UUID eventId;
    private final UUID propertyId;
    private final UUID tenantId;
    private final String correlationId;
    private final Instant occurredAt;

    protected BasePropertyEventDto(UUID propertyId, UUID tenantId, String correlationId) {
        this.eventId = UUID.randomUUID();
        this.propertyId = propertyId;
        this.tenantId = tenantId;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }
}