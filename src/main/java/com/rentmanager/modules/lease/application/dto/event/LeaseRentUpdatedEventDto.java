package com.rentmanager.modules.lease.application.dto.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when lease rent is changed.
 */
public class LeaseRentUpdatedEventDto extends BaseLeaseEventDto {

    private final UUID leaseId;
    private final BigDecimal oldRent;
    private final BigDecimal newRent;

    public LeaseRentUpdatedEventDto(
            UUID eventId,
            UUID tenantId,
            String correlationId,
            String actor,
            UUID leaseId,
            BigDecimal oldRent,
            BigDecimal newRent,
            Instant occurredAt
    ) {
        super(eventId, tenantId, correlationId, actor, occurredAt);

        this.leaseId = leaseId;
        this.oldRent = oldRent;
        this.newRent = newRent;
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public BigDecimal getOldRent() {
        return oldRent;
    }

    public BigDecimal getNewRent() {
        return newRent;
    }
}