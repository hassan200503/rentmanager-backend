package com.rentmanager.modules.lease.application.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when lease security deposit is updated.
 *
 * SaaS-grade transport event:
 * - tenant-safe
 * - fully traceable (correlationId + actor)
 * - immutable
 * - projection-friendly
 */
public final class LeaseSecurityDepositUpdatedEvent extends BaseLeaseEventDto {

    private final UUID leaseId;
    private final BigDecimal oldDeposit;
    private final BigDecimal newDeposit;

    public LeaseSecurityDepositUpdatedEvent(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            Instant occurredAt,
            UUID leaseId,
            BigDecimal oldDeposit,
            BigDecimal newDeposit
    ) {
        super(eventId, tenantId, correlationId, actor, occurredAt);
        this.leaseId = leaseId;
        this.oldDeposit = oldDeposit;
        this.newDeposit = newDeposit;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public BigDecimal getOldDeposit() {
        return oldDeposit;
    }

    public BigDecimal getNewDeposit() {
        return newDeposit;
    }
}