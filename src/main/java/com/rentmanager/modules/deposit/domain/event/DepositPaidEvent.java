package com.rentmanager.modules.deposit.domain.event;

import com.rentmanager.domain.base.DomainEvent;
import java.util.UUID;

public class DepositPaidEvent extends DomainEvent {

    private final UUID leaseId;
    private final UUID unitId;
    private final UUID tenantProfileId;

    public DepositPaidEvent(
            UUID tenantId,
            UUID aggregateId,
            String actor,
            UUID leaseId,
            UUID unitId,
            UUID tenantProfileId
    ) {
        super(tenantId, aggregateId, actor);
        this.leaseId = leaseId;
        this.unitId = unitId;
        this.tenantProfileId = tenantProfileId;
    }

    @Override
    public String eventType() {
        return "DEPOSIT_PAID";
    }

    public UUID getLeaseId() {
        return leaseId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getTenantProfileId() {
        return tenantProfileId;
    }
}