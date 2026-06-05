package com.rentmanager.modules.property.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class PropertyOccupancyChangedEvent extends DomainEvent {

    private final UUID propertyId;
    private final int previousOccupancy;
    private final int newOccupancy;

    public PropertyOccupancyChangedEvent(
            UUID tenantId,
            UUID aggregateId,
            String correlationId,
            UUID propertyId,
            int previousOccupancy,
            int newOccupancy
    ) {
        super(tenantId,aggregateId, correlationId);
        this.propertyId = propertyId;
        this.previousOccupancy = previousOccupancy;
        this.newOccupancy = newOccupancy;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public int getPreviousOccupancy() {
        return previousOccupancy;
    }

    public int getNewOccupancy() {
        return newOccupancy;
    }

    @Override
    public String eventType() {
        return "PROPERTY_OCCUPANCY_CHANGED";
    }
}