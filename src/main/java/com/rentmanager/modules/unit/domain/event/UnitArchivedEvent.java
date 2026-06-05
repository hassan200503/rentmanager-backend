package com.rentmanager.modules.unit.domain.event;

import com.rentmanager.domain.base.DomainEvent;
import lombok.Getter;

import java.util.UUID;

@Getter
public class UnitArchivedEvent extends DomainEvent {

    private final UUID tenantId;
    private final UUID unitId;
    private final String correlationId;
    private final String reason;

    public UnitArchivedEvent(
            UUID tenantId,
            UUID unitId,
            String correlationId,
            String reason
    ) {
        super(tenantId, unitId, correlationId);

        this.tenantId = tenantId;
        this.unitId = unitId;
        this.correlationId = correlationId;
        this.reason = reason;
    }

    @Override
    public String eventType() {
        return "UNIT_ARCHIVED";
    }
}