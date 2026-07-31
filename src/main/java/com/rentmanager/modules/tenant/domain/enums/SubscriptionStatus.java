package com.rentmanager.modules.tenant.domain.enums;

public enum SubscriptionStatus {
    TRIAL,
    ACTIVE,
    LAPSED,
    CANCELLED,
    PAST_DUE,

    /** Premium monthly: renewal payment failed; features still work until
     * the grace window ends, then the scheduler reverts to COMMISSION. */
    GRACE_PERIOD
}