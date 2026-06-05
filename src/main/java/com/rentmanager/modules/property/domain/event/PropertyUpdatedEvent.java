package com.rentmanager.modules.property.domain.event;

import java.time.Instant;
import java.util.UUID;

public class PropertyUpdatedEvent {

    private final UUID propertyId;
    private final UUID tenantId;
    private final String propertyCode;
    private final String propertyName;
    private final Instant occurredAt;

    public PropertyUpdatedEvent(
            UUID propertyId,
            UUID tenantId,
            String propertyCode,
            String propertyName,
            Instant occurredAt
    ) {
        this.propertyId = propertyId;
        this.tenantId = tenantId;
        this.propertyCode = propertyCode;
        this.propertyName = propertyName;
        this.occurredAt = occurredAt;
    }

    public static PropertyUpdatedEvent of(
            UUID propertyId,
            UUID tenantId,
            String propertyCode,
            String propertyName
    ) {
        return new PropertyUpdatedEvent(
                propertyId,
                tenantId,
                propertyCode,
                propertyName,
                Instant.now()
        );
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPropertyCode() {
        return propertyCode;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}