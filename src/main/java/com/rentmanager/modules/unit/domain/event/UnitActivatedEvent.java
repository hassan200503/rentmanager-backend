package com.rentmanager.modules.unit.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.util.UUID;

public class UnitActivatedEvent extends DomainEvent {

    private final UUID unitId;

    public UnitActivatedEvent(UUID tenantId,UUID aggregateId, String correlationId, UUID unitId) {
        super(tenantId,aggregateId, correlationId);
        this.unitId = unitId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    @Override
    public String eventType() {
        return "UNIT_ACTIVATED";
    }
}