package com.rentmanager.modules.lease.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.time.LocalDate;
import java.util.UUID;

public class LeaseRenewedEvent extends DomainEvent {

    private final LocalDate newStartDate;
    private final LocalDate newEndDate;

    public LeaseRenewedEvent(
            UUID tenantId,
            UUID aggregateId,
            String actor,
            LocalDate newStartDate,
            LocalDate newEndDate
    ) {
        super(tenantId, aggregateId, actor);

        this.newStartDate = newStartDate;
        this.newEndDate = newEndDate;
    }

    @Override
    public String eventType() {
        return "LEASE_RENEWED";
    }

    public LocalDate getNewStartDate() {
        return newStartDate;
    }

    public LocalDate getNewEndDate() {
        return newEndDate;
    }
}