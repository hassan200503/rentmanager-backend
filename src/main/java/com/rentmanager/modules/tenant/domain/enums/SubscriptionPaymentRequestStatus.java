package com.rentmanager.modules.tenant.domain.enums;

/**
 * Status of a {@code SubscriptionPaymentRequest}. Mirrors
 * {@code RentPaymentRequestStatus} plus {@code EXPIRED}, which that flow
 * deliberately lacks because it has no stale-request sweep -- subscription
 * billing does have one ({@code SubscriptionBillingSweepService}), so the
 * late-payment reconciliation case exists here.
 *
 * Late success is honored: a success callback for a request the sweep
 * already marked {@code EXPIRED} (or a failed one) is still applied, since
 * a confirmed M-Pesa receipt means the money moved -- never locking a
 * landlord out of a paid-for premium period is a hard requirement.
 */
public enum SubscriptionPaymentRequestStatus {

    /** STK push sent (or about to be), awaiting the Daraja callback. */
    PENDING,

    /** Daraja callback confirmed success; applied to the subscription. */
    PAID,

    /** Daraja callback reported failure, or no receipt number was present. */
    FAILED,

    /** No callback received within the stale window; swept by the scheduler. */
    EXPIRED
}
