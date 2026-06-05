package com.rentmanager.modules.unit.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class UnitCreatedEvent extends DomainEvent {

    private final UUID unitId;

    public UnitCreatedEvent(UUID tenantId,UUID aggregateId, String correlationId, UUID unitId) {
        super(tenantId,aggregateId, correlationId);
        this.unitId = unitId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    @Override
    public String eventType() {
        return "UNIT_CREATED";
    }
}