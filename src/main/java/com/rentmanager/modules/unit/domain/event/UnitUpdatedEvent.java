package com.rentmanager.modules.unit.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

public class UnitUpdatedEvent extends DomainEvent {

    private final UUID unitId;
    private final String unitNumber;
    private final String label;
    private final BigDecimal rentAmount;
    private final String description;

    public UnitUpdatedEvent(
            UUID tenantId,
            UUID aggregateId,
            String correlationId,
            UUID unitId,
            String unitNumber,
            String label,
            BigDecimal rentAmount,
            String description
    ) {
        super(tenantId,aggregateId, correlationId);
        this.unitId = unitId;
        this.unitNumber = unitNumber;
        this.label = label;
        this.rentAmount = rentAmount;
        this.description = description;
    }

    @Override
    public String eventType() {
        return "UNIT_UPDATED";
    }

    public UUID getUnitId() {
        return unitId;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getRentAmount() {
        return rentAmount;
    }

    public String getDescription() {
        return description;
    }
}