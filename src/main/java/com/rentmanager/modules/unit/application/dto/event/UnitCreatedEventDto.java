package com.rentmanager.modules.unit.application.dto.event;

import com.rentmanager.modules.unit.domain.enums.UnitStatus;

import java.util.UUID;

public class UnitCreatedEventDto extends BaseUnitEventDto {

    private final String unitNumber;
    private final Double rentAmount;
    private final UnitStatus status;

    public UnitCreatedEventDto(
            UUID unitId,
            UUID tenantId,
            String correlationId,
            String unitNumber,
            Double rentAmount,
            UnitStatus status
    ) {
        super(unitId, tenantId, correlationId);
        this.unitNumber = unitNumber;
        this.rentAmount = rentAmount;
        this.status = status;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public Double getRentAmount() {
        return rentAmount;
    }

    public UnitStatus getStatus() {
        return status;
    }
}