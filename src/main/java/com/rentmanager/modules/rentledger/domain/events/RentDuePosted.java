package com.rentmanager.modules.rentledger.domain.events;

import com.rentmanager.domain.base.DomainEvent;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fired when a new RentLedgerEntry is created — either at lease activation
 * (Phase 1) or by the recurring collection scheduler (Phase 3). Consumed
 * downstream by Phase 3 (to potentially trigger an STK push) and Phase 4
 * (reminders, which read due dates off the ledger).
 *
 * Constructor argument order (tenantId, aggregateId, correlationId,
 * ...specifics) is VERIFIED against DomainEvent.java's actual super()
 * signature: {@code protected DomainEvent(UUID tenantId, UUID aggregateId,
 * String correlationId)}.
 *
 * NOT VERIFIED: the string returned by eventType(). DomainEvent.java
 * declares it abstract with no example implementation available, so
 * "RentDuePosted" (the class's own simple name) is used as the most
 * conservative default. If the rest of the codebase uses a different
 * convention (e.g. a dotted "rentledger.rent_due_posted" style, or a
 * constants class), this needs a one-line fix once you share an existing
 * eventType() override for comparison.
 */
@Getter
public class RentDuePosted extends DomainEvent {

    private final UUID ledgerEntryId;
    private final UUID leaseId;
    private final UUID unitId;
    private final UUID tenantProfileId;
    private final BigDecimal amountDue;
    private final LocalDate dueDate;

    public RentDuePosted(
            UUID tenantId,
            UUID ledgerEntryId,
            String correlationId,
            UUID leaseId,
            UUID unitId,
            UUID tenantProfileId,
            BigDecimal amountDue,
            LocalDate dueDate
    ) {
        super(tenantId, ledgerEntryId, correlationId);
        this.ledgerEntryId = ledgerEntryId;
        this.leaseId = leaseId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
        this.amountDue = amountDue;
        this.dueDate = dueDate;
    }

    @Override
    public String eventType() {
        return "RentDuePosted";
    }
}