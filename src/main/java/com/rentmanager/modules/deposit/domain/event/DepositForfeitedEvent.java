package com.rentmanager.modules.deposit.domain.event;

import com.rentmanager.domain.base.DomainEvent;
import java.util.UUID;

public class DepositForfeitedEvent extends DomainEvent {

    private final UUID leaseId;

    public DepositForfeitedEvent(UUID tenantId, UUID aggregateId, String actor, UUID leaseId) {
        super(tenantId, aggregateId, actor);
        this.leaseId = leaseId;
    }

    @Override
    public String eventType() {
        return "DEPOSIT_FORFEITED";
    }

    public UUID getLeaseId() {
        return leaseId;
    }
}