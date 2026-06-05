package com.rentmanager.modules.property.application.dto.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event DTO emitted when a Property is updated.
 */
public class PropertyUpdatedEventDto extends BasePropertyEventDto {

    private final Object previousState;
    private final Object updatedState;
    private final String updatedBy;
    private final Instant updatedAt;

    public PropertyUpdatedEventDto(
            UUID propertyId,
            UUID tenantId,
            String correlationId,
            Object previousState,
            Object updatedState,
            String updatedBy,
            Instant updatedAt
    ) {
        super(propertyId, tenantId, correlationId);
        this.previousState = previousState;
        this.updatedState = updatedState;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public Object getPreviousState() {
        return previousState;
    }

    public Object getUpdatedState() {
        return updatedState;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}