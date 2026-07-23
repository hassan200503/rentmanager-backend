package com.rentmanager.modules.rentledger.domain.enums;

/**
 * Status of a {@code RentPaymentRequest}. Deliberately a three-value subset
 * of {@code PaymentIntentStatus} (no EXPIRED / PAID_AFTER_EXPIRY) — there is
 * no scheduled stale-request sweep for rent payments yet (the equivalent of
 * {@code PaymentIntentExpiryScheduler}), so there is no "expired but a real
 * payment landed late" case to reconcile until that scheduler exists. If a
 * sweep is added later, EXPIRED and PAID_AFTER_EXPIRY should be added here
 * at the same time, along with the corresponding branch in
 * {@code RentPaymentCallbackService} that {@code MpesaCallbackService}
 * already has for the deposit flow.
 */
public enum RentPaymentRequestStatus {

    /** STK push sent (or about to be), awaiting the Daraja callback. */
    PENDING,

    /** Daraja callback confirmed success; applied to the rent ledger. */
    PAID,

    /** Daraja callback reported failure, or no receipt number was present. */
    FAILED
}
