package com.rentmanager.modules.unit.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.event.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Unit extends AggregateRoot {

    private UUID tenantId;
    private UUID propertyId;
    private String unitNumber;
    private String label;
    private UnitStatus status;
    private UnitOccupancyStatus occupancyStatus;
    private BigDecimal rentAmount;
    private String description;

    public static Unit create(
            UUID tenantId,
            UUID propertyId,
            String unitNumber,
            String label,
            BigDecimal rentAmount,
            String description,
            String correlationId
    ) {

        Unit unit = Unit.builder()
                .tenantId(tenantId)
                .propertyId(propertyId)
                .unitNumber(unitNumber)
                .label(label)
                .status(UnitStatus.INACTIVE)
                .occupancyStatus(UnitOccupancyStatus.VACANT)
                .rentAmount(rentAmount)
                .description(description)
                .build();

        unit.registerEvent(new UnitCreatedEvent(
                tenantId,
                unit.getId(),
                correlationId,
                unit.getId()
        ));

        return unit;
    }

    public void updateDetails(
            String unitNumber,
            String label,
            BigDecimal rentAmount,
            String description,
            String correlationId
    ) {
        this.unitNumber = unitNumber;
        this.label = label;
        this.rentAmount = rentAmount;
        this.description = description;

        registerEvent(new UnitUpdatedEvent(
                tenantId,
                getId(),
                correlationId,
                getId(),
                this.unitNumber,
                this.label,
                this.rentAmount,
                this.description
        ));
    }

    public void activate(String correlationId) {
        if (this.status == UnitStatus.ACTIVE) return;

        this.status = UnitStatus.ACTIVE;

        registerEvent(new UnitActivatedEvent(
                tenantId,
                getId(),
                correlationId,
                getId()
        ));
    }

    public void deactivate(String correlationId) {
        if (this.status == UnitStatus.INACTIVE) return;

        this.status = UnitStatus.INACTIVE;

        registerEvent(new UnitDeactivatedEvent(
                tenantId,
                getId(),
                correlationId,
                getId()
        ));
    }

    public void markOccupied(String correlationId) {
        if (this.occupancyStatus == UnitOccupancyStatus.OCCUPIED) return;

        UnitOccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = UnitOccupancyStatus.OCCUPIED;

        registerEvent(new UnitOccupancyChangedEvent(
                tenantId,
                getId(),
                correlationId,
                getId(),
                previous,
                this.occupancyStatus
        ));
    }

    public void markVacant(String correlationId) {
        if (this.occupancyStatus == UnitOccupancyStatus.VACANT) return;

        UnitOccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = UnitOccupancyStatus.VACANT;

        registerEvent(new UnitOccupancyChangedEvent(
                tenantId,
                getId(),
                correlationId,
                getId(),
                previous,
                this.occupancyStatus
        ));
    }

    public void archive(String correlationId) {

        this.status = UnitStatus.ARCHIVED;

        registerEvent(new UnitArchivedEvent(
                tenantId,
                getId(),
                correlationId,
                "SYSTEM"
        ));
    }


    public static Unit rehydrate(
            UUID id,
            UUID tenantId,
            UUID propertyId,
            String unitNumber,
            String label,
            UnitStatus status,
            UnitOccupancyStatus occupancyStatus,
            BigDecimal rentAmount,
            String description
    ) {
        Unit unit = Unit.builder()
                .tenantId(tenantId)
                .propertyId(propertyId)
                .unitNumber(unitNumber)
                .label(label)
                .status(status)
                .occupancyStatus(occupancyStatus)
                .rentAmount(rentAmount)
                .description(description)
                .build();

        return unit;
    }



    public Integer getFloor() {
        return null;
    }

    public String getStatusAsString() {
        return this.status != null ? this.status.name() : null;


    }
}


