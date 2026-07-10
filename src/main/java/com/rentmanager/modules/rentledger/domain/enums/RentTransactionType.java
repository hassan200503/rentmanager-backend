package com.rentmanager.modules.rentledger.domain.enums;

/**
 * Classifies a {@code RentTransaction}. {@code amount} on the transaction is
 * always stored positive; the direction (increases amount owed vs. reduces
 * it) is implied by the type, not the sign — this keeps the append-only log
 * unambiguous to read without needing to reconstruct intent from a signed
 * number.
 */
public enum RentTransactionType {

    /** Increases the balance owed. Posted once per period at entry creation. */
    RENT_CHARGE,

    /** Reduces the balance owed. The normal case — a tenant payment landing. */
    PAYMENT,

    /** Reduces the balance owed. An admin decision to forgive part/all of a charge. */
    WAIVER,

    /** Reduces amount_paid. Used when resolving an OVERPAID entry via refund. */
    REFUND,

    /**
     * Reduces the balance owed. Used when an OVERPAID entry on a prior period
     * is resolved by applying its excess to THIS period's charge.
     */
    CREDIT_APPLIED,

    /** Reduces or increases the balance owed. A manual correction, always with a reason recorded via recordedBy/source. */
    ADJUSTMENT
}