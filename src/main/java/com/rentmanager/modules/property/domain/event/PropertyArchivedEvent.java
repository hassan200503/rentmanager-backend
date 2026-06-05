package com.rentmanager.modules.property.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class PropertyArchivedEvent extends DomainEvent {

    private final UUID propertyId;

    public PropertyArchivedEvent(UUID tenantId, UUID aggregateId, String correlationId, UUID propertyId) {
        super(tenantId, aggregateId, correlationId);
        this.propertyId = propertyId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    @Override
    public String eventType() {
        return "PROPERTY_ARCHIVED";
    }
}