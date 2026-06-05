package com.rentmanager.modules.lease.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.math.BigDecimal;
import java.util.UUID;

public class LeaseDepositUpdatedEvent extends DomainEvent {

    private final UUID propertyId;
    private final UUID unitId;
    private final UUID tenantProfileId;

    private final BigDecimal oldDeposit;
    private final BigDecimal newDeposit;

    public LeaseDepositUpdatedEvent(
            UUID tenantId,
            UUID aggregateId,
            String actor,
            UUID propertyId,
            UUID unitId,
            UUID tenantProfileId,
            BigDecimal oldDeposit,
            BigDecimal newDeposit
    ) {
        super(tenantId, aggregateId, actor);

        this.propertyId = propertyId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
        this.oldDeposit = oldDeposit;
        this.newDeposit = newDeposit;
    }

    @Override
    public String eventType() {
        return "LEASE_DEPOSIT_UPDATED";
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getTenantProfileId() {
        return tenantProfileId;
    }

    public BigDecimal getOldDeposit() {
        return oldDeposit;
    }

    public BigDecimal getNewDeposit() {
        return newDeposit;
    }
}