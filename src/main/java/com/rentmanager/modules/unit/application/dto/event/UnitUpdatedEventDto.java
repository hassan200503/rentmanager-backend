package com.rentmanager.modules.unit.application.dto.event;

import java.util.UUID;

public class UnitUpdatedEventDto extends BaseUnitEventDto {

    private final String unitNumber;
    private final Double rentAmount;
    private final String description;

    public UnitUpdatedEventDto(
            UUID unitId,
            UUID tenantId,
            String correlationId,
            String unitNumber,
            Double rentAmount,
            String description
    ) {
        super(unitId, tenantId, correlationId);
        this.unitNumber = unitNumber;
        this.rentAmount = rentAmount;
        this.description = description;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public Double getRentAmount() {
        return rentAmount;
    }

    public String getDescription() {
        return description;
    }
}