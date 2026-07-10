package com.rentmanager.modules.rentledger.domain.events;

import com.rentmanager.domain.base.DomainEvent;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fired every time a transaction that reduces the balance owed (PAYMENT,
 * WAIVER, or CREDIT_APPLIED) is applied to a RentLedgerEntry and the entry's
 * status is recomputed. Consumed downstream by Phase 4 (cancel pending
 * reminders once an entry is PAID) and Phase 6 (dashboard/notifications).
 *
 * {@code transactionId} lets a consumer look up the exact RentTransaction
 * row (amount, source, external reference) without this event needing to
 * carry that detail itself — same "point at the transaction, don't
 * duplicate it" reasoning used elsewhere in this module.
 */
@Getter
public class RentPaymentApplied extends DomainEvent {

    private final UUID ledgerEntryId;
    private final UUID leaseId;
    private final UUID transactionId;
    private final BigDecimal amountApplied;
    private final RentLedgerStatus newStatus;

    public RentPaymentApplied(
            UUID tenantId,
            UUID ledgerEntryId,
            String correlationId,
            UUID leaseId,
            UUID transactionId,
            BigDecimal amountApplied,
            RentLedgerStatus newStatus
    ) {
        super(tenantId, ledgerEntryId, correlationId);
        this.ledgerEntryId = ledgerEntryId;
        this.leaseId = leaseId;
        this.transactionId = transactionId;
        this.amountApplied = amountApplied;
        this.newStatus = newStatus;
    }

    @Override
    public String eventType() {
        return "RentPaymentApplied";
    }
}