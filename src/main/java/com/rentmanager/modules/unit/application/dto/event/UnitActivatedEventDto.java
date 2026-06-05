package com.rentmanager.modules.unit.application.dto.event;

import com.rentmanager.modules.unit.domain.enums.UnitStatus;

import java.util.UUID;

public class UnitActivatedEventDto extends BaseUnitEventDto {

    private final UnitStatus previousStatus;
    private final UnitStatus newStatus;

    public UnitActivatedEventDto(
            UUID unitId,
            UUID tenantId,
            String correlationId,
            UnitStatus previousStatus,
            UnitStatus newStatus
    ) {
        super(unitId, tenantId, correlationId);
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    public UnitStatus getPreviousStatus() {
        return previousStatus;
    }

    public UnitStatus getNewStatus() {
        return newStatus;
    }
}