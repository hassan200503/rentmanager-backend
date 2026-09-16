package com.rentmanager.modules.lease.domain.enums;

public enum LeaseStatus {

    DRAFT,

    PENDING_APPROVAL,

    AWAITING_DEPOSIT,
    ACTIVE,

    EXPIRED,

    PENDING_ACTIVATION,

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

    /**
     * Cancellation is for a lease that never went live: nobody has moved in
     * and no rent has been charged. A RENEWED lease is occupied and billing
     * exactly like an ACTIVE one, and a lease that has already ended cannot be
     * cancelled after the fact; those end by termination or expiry.
     */
    public boolean isCancellable() {
        return this == DRAFT || this == PENDING_APPROVAL || this == AWAITING_DEPOSIT || this == PENDING_ACTIVATION;
    }
}
