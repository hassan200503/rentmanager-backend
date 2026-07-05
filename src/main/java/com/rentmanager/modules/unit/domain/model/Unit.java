package com.rentmanager.modules.unit.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.event.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private LocalDateTime vacatedAt;

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
                .vacatedAt(LocalDateTime.now())
                .rentAmount(rentAmount)
                .description(description)
                .build();

        unit.setId(UUID.randomUUID());

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
        this.vacatedAt = null;

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
        this.vacatedAt = LocalDateTime.now();

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
            String description,
            LocalDateTime vacatedAt
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
                .vacatedAt(vacatedAt)
                .build();

        unit.setId(id);
        return unit;
    }

    public Integer getFloor() {
        return null;
    }

    public String getStatusAsString() {
        return this.status != null ? this.status.name() : null;
    }

    public void markReserved(String correlationId) {
        if (this.occupancyStatus == UnitOccupancyStatus.RESERVED) return;

        UnitOccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = UnitOccupancyStatus.RESERVED;
        this.vacatedAt = null;

        registerEvent(new UnitOccupancyChangedEvent(
                tenantId, getId(), correlationId, getId(), previous, this.occupancyStatus
        ));
    }

    /**
     * Compensating action for a failed reservation fulfillment saga.
     * Reverts a RESERVED unit back to VACANT so it can be reserved again.
     * No-op if the unit isn't currently RESERVED (e.g. compensation running
     * twice, or this step never actually completed before the failure).
     */
    public void releaseReservation(String correlationId) {
        if (this.occupancyStatus != UnitOccupancyStatus.RESERVED) return;

        UnitOccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = UnitOccupancyStatus.VACANT;
        this.vacatedAt = LocalDateTime.now();

        registerEvent(new UnitOccupancyChangedEvent(
                tenantId, getId(), correlationId, getId(), previous, this.occupancyStatus
        ));
    }

    /**
     * Marks the unit as held while an M-Pesa STK push is in flight for a
     * reservation attempt. Must only be called from within a transaction
     * that holds a pessimistic write lock on this unit's row (see
     * UnitRepository#findByIdForUpdate), so that two concurrent reservation
     * attempts on the same unit cannot both observe VACANT and both
     * transition through here.
     *
     * Throws rather than no-oping on an illegal starting state: unlike the
     * other transitions on this aggregate, reaching this method with the
     * unit already in PENDING_PAYMENT/RESERVED/OCCUPIED means the guard
     * this method exists to provide has already failed upstream (e.g. the
     * pessimistic lock wasn't actually acquired, or a caller bypassed the
     * lock), and that should surface loudly rather than be silently
     * absorbed.
     */
    public void markPendingPayment(String correlationId) {
        if (this.occupancyStatus != UnitOccupancyStatus.VACANT) {
            throw new IllegalStateException(
                    "Cannot initiate reservation payment for unit " + getId() +
                            ": expected VACANT but was " + this.occupancyStatus
            );
        }

        UnitOccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = UnitOccupancyStatus.PENDING_PAYMENT;

        registerEvent(new UnitOccupancyChangedEvent(
                tenantId, getId(), correlationId, getId(), previous, this.occupancyStatus
        ));
    }

    /**
     * Compensating action for a failed or expired STK push: reverts a
     * PENDING_PAYMENT unit back to VACANT so it becomes reservable again.
     * No-op if the unit isn't currently PENDING_PAYMENT — in particular,
     * this deliberately does NOT touch a RESERVED or OCCUPIED unit, since a
     * stale or duplicate M-Pesa callback firing this after the unit has
     * legitimately moved on must never vacate it out from under a real
     * tenant.
     */
    public void releasePendingPayment(String correlationId) {
        if (this.occupancyStatus != UnitOccupancyStatus.PENDING_PAYMENT) return;

        UnitOccupancyStatus previous = this.occupancyStatus;
        this.occupancyStatus = UnitOccupancyStatus.VACANT;
        this.vacatedAt = LocalDateTime.now();

        registerEvent(new UnitOccupancyChangedEvent(
                tenantId, getId(), correlationId, getId(), previous, this.occupancyStatus
        ));
    }
}