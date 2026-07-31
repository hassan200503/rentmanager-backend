package com.rentmanager.modules.tenant.domain.enums;

/**
 * What a {@code SubscriptionPaymentRequest} is paying for.
 */
public enum SubscriptionPaymentPurpose {

    /** First payment when switching COMMISSION -> PREMIUM_MONTHLY. */
    INITIAL_ACTIVATION,

    /** Monthly renewal of an already-active premium subscription. */
    RENEWAL
}
