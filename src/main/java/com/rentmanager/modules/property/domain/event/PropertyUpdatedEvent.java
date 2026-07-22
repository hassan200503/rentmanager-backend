package com.rentmanager.modules.property.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class PropertyUpdatedEvent extends DomainEvent {

    private final UUID propertyId;

    public PropertyUpdatedEvent(UUID tenantId, String correlationId, UUID aggregateId, UUID propertyId) {
        super(tenantId, aggregateId, correlationId);
        this.propertyId = propertyId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    @Override
    public String eventType() {
        return "PROPERTY_UPDATED";
    }
}
