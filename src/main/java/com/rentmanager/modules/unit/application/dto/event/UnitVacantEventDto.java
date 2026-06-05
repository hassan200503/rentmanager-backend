package com.rentmanager.modules.unit.application.dto.event;

import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;

import java.util.UUID;

public class UnitVacantEventDto extends BaseUnitEventDto {

    private final UnitOccupancyStatus previousStatus;
    private final UnitOccupancyStatus newStatus;

    public UnitVacantEventDto(
            UUID unitId,
            UUID tenantId,
            String correlationId,
            UnitOccupancyStatus previousStatus,
            UnitOccupancyStatus newStatus
    ) {
        super(unitId, tenantId, correlationId);
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
    }

    public UnitOccupancyStatus getPreviousStatus() {
        return previousStatus;
    }

    public UnitOccupancyStatus getNewStatus() {
        return newStatus;
    }
}