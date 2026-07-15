package com.rentmanager.modules.unit.domain.event;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;

import java.util.UUID;

public class UnitOccupancyChangedEvent extends DomainEvent {

    private final UUID unitId;
    private final UUID propertyId;
    private final UnitOccupancyStatus previousStatus;
    private final UnitOccupancyStatus newStatus;

    public UnitOccupancyChangedEvent(
            UUID tenantId,
            UUID aggregateId,
            String correlationId,
            UUID unitId,
            UUID propertyId,
            UnitOccupancyStatus previousStatus,
            UnitOccupancyStatus newStatus
    ) {
        super(tenantId, aggregateId, correlationId);
        this.unitId = unitId;
        this.propertyId = propertyId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UnitOccupancyStatus getPreviousStatus() {
        return previousStatus;
    }

    public UnitOccupancyStatus getNewStatus() {
        return newStatus;
    }

    @Override
    public String eventType() {
        return "UNIT_OCCUPANCY_CHANGED";
    }
}