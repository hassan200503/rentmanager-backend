package com.rentmanager.modules.lease.domain.enums;

public enum LeaseStatus {

    DRAFT,

    PENDING_APPROVAL,

    AWAITING_DEPOSIT,
    ACTIVE,

    EXPIRED,

    RENEWED,
    CANCELLED,

    TERMINATED,

    SUSPENDED;



    public boolean isTerminated() {
        return this == TERMINATED;
    }

    public boolean isCancelled() {
        return this == CANCELLED;
    }
}
