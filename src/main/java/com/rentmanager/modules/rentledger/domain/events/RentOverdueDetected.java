package com.rentmanager.modules.rentledger.domain.events;

import com.rentmanager.domain.base.DomainEvent;
import lombok.Getter;

import java.util.UUID;

/**
 * Fired by {@code RentLedgerEntry#markOverdue(int)} when a scheduler
 * (Phase 4's territory to call, though the transition itself lives on this
 * aggregate per the locked design) detects that due_date + grace period has
 * elapsed with no full payment. This is Phase 4's primary trigger for
 * tenant reminders and landlord notifications.
 */
@Getter
public class RentOverdueDetected extends DomainEvent {

    private final UUID ledgerEntryId;
    private final UUID leaseId;
    private final UUID tenantProfileId;
    private final int daysOverdue;

    public RentOverdueDetected(
            UUID tenantId,
            UUID ledgerEntryId,
            String correlationId,
            UUID leaseId,
            UUID tenantProfileId,
            int daysOverdue
    ) {
        super(tenantId, ledgerEntryId, correlationId);
        this.ledgerEntryId = ledgerEntryId;
        this.leaseId = leaseId;
        this.tenantProfileId = tenantProfileId;
        this.daysOverdue = daysOverdue;
    }

    @Override
    public String eventType() {
        return "RentOverdueDetected";
    }
}