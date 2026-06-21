package com.rentmanager.modules.deposit.domain.event;

import com.rentmanager.domain.base.DomainEvent;
import java.math.BigDecimal;
import java.util.UUID;

public class DepositRefundedEvent extends DomainEvent {

    private final UUID leaseId;
    private final BigDecimal refundAmount;

    public DepositRefundedEvent(
            UUID tenantId,
            UUID aggregateId,
            String actor,
            UUID leaseId,
            BigDecimal refundAmount
    ) {
        super(tenantId, aggregateId, actor);
        this.leaseId = leaseId;
        this.refundAmount = refundAmount;
    }

    @Override
    public String eventType() {
        return "DEPOSIT_REFUNDED";
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }
}