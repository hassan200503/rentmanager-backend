package com.rentmanager.modules.rentledger.domain.enums;

/**
 * Origin of a {@code RentTransaction}, for audit trail and fraud-review
 * purposes (Phase 7 reads this). Distinct from {@code RentTransactionType},
 * which says WHAT happened; this says WHERE it came from.
 */
public enum RentTransactionSource {

    /** Confirmed via an M-Pesa STK push / C2B callback (Phase 3's own callback service, not MpesaCallbackService). */
    MPESA,

    /** Recorded manually by a landlord/admin for an off-platform cash payment. */
    CASH,

    /** A correction, waiver, or refund entered by an admin. Always paired with a non-SYSTEM recordedBy. */
    ADMIN_ADJUSTMENT,

    /** Posted by a scheduler or internal process with no human actor (e.g. the recurring RENT_CHARGE post). */
    SYSTEM
}