package com.rentmanager.modules.rentledger.domain.events;

import com.rentmanager.domain.base.DomainEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fired the moment a RentLedgerEntry transitions into OVERPAID — i.e. the
 * first recompute where amountPaid exceeds amountDue. Deliberately fired
 * only on the transition INTO OVERPAID, not on every subsequent recompute
 * while it remains OVERPAID, so downstream consumers (e.g. an admin task
 * queue for Phase 6) get exactly one notification per overpayment episode
 * rather than one per redundant re-check.
 */
@Getter
public class RentOverpaymentDetected extends DomainEvent {

    private final UUID ledgerEntryId;
    private final UUID leaseId;
    private final BigDecimal excessAmount;

    public RentOverpaymentDetected(
            UUID tenantId,
            UUID ledgerEntryId,
            String correlationId,
            UUID leaseId,
            BigDecimal excessAmount
    ) {
        super(tenantId, ledgerEntryId, correlationId);
        this.ledgerEntryId = ledgerEntryId;
        this.leaseId = leaseId;
        this.excessAmount = excessAmount;
    }

    @Override
    public String eventType() {
        return "RentOverpaymentDetected";
    }
}