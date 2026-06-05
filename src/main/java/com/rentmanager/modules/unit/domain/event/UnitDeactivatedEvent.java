package com.rentmanager.modules.unit.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class UnitDeactivatedEvent extends DomainEvent {

    private final UUID unitId;

    public UnitDeactivatedEvent(UUID tenantId,UUID aggregateId, String correlationId, UUID unitId) {
        super(tenantId,aggregateId, correlationId);
        this.unitId = unitId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    @Override
    public String eventType() {
        return "UNIT_DEACTIVATED";
    }
}