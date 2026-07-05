package com.rentmanager.modules.reservation.domain.enums;

public enum PaymentIntentStatus {

    PENDING,             // STK push sent, waiting for customer PIN
    PAID,                // M-Pesa callback confirmed payment while still PENDING
    FAILED,              // M-Pesa callback reported failure
    EXPIRED,             // stale-intent sweep timed it out before any callback arrived
    PAID_AFTER_EXPIRY    // a genuine successful M-Pesa callback arrived AFTER the stale-intent
    // sweep had already expired this intent and released the unit.
    // Money was taken but no reservation was created and the unit was
    // not touched (it may already belong to a different applicant).
    // Requires manual reconciliation/refund review — see
    // MpesaCallbackService's CRITICAL log for details.
}