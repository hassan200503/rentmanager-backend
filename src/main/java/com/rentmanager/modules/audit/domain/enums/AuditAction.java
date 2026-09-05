package com.rentmanager.modules.audit.domain.enums;

/**
 * The things worth recording that somebody did.
 *
 * <h2>Money was entirely absent from this enum</h2>
 * It carried thirteen constants covering login, users, units and leases, and
 * not one financial action. The one domain where an audit trail is the whole
 * point — who moved money, to where, and against what balance — had no
 * vocabulary here at all.
 *
 * <p>{@code rent_transactions} is append-only (V81), so the ledger already
 * proves <em>that</em> money moved and that the row was never edited
 * afterwards. What it cannot say is who authorised it. These actions close
 * that gap; the two together are the difference between a tamper-evident
 * ledger and an accountable one.
 */
public enum AuditAction {

    // ── Auth ─────────────────────────────────────────────────────────────
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,

    // ── User ─────────────────────────────────────────────────────────────
    USER_CREATED,
    USER_UPDATED,
    USER_DELETED,

    // ── Unit ─────────────────────────────────────────────────────────────
    UNIT_CREATED,
    UNIT_UPDATED,
    UNIT_DELETED,

    // ── Lease ────────────────────────────────────────────────────────────
    LEASE_CREATED,
    LEASE_UPDATED,
    LEASE_TERMINATED,

    // ── Money in ─────────────────────────────────────────────────────────
    /** A payment was applied to a ledger entry. */
    PAYMENT_RECORDED,
    /** A posted transaction was voided by a compensating entry (V71). */
    PAYMENT_REVERSED,
    /**
     * An orphan M-Pesa receipt was pointed at a lease by a human. The most
     * ordinary fraud in property management is crediting a renter's payment
     * to the wrong lease, and until now that decision left no trace at all.
     */
    UNMATCHED_PAYMENT_RESOLVED,
    /** An OVERPAID entry was settled as a credit or a refund. */
    OVERPAYMENT_RESOLVED,
    /** A manual correction was posted against a ledger entry. */
    LEDGER_ADJUSTMENT_POSTED,

    // ── Money out ────────────────────────────────────────────────────────
    /** A payout to a landlord was raised. */
    DISBURSEMENT_INITIATED,
    /** Safaricom confirmed a payout. */
    DISBURSEMENT_COMPLETED,
    /** A payout failed, whether at initiation or on the result callback. */
    DISBURSEMENT_FAILED,
    /** A deposit was returned to a renter. */
    DEPOSIT_REFUNDED,
    /** A deposit was kept by the landlord. */
    DEPOSIT_FORFEITED,

    // ── Settings that decide how much money moves ────────────────────────
    /**
     * The destination for every future payout on this account changed.
     * Account-takeover fraud almost always begins here, which makes this the
     * single most valuable row in the table.
     */
    PAYOUT_DESTINATION_CHANGED,
    /** The commission rate applied to this landlord's rent changed. */
    COMMISSION_POLICY_CHANGED,
    /** Daraja credentials were added or rotated. */
    PAYMENT_CREDENTIALS_CHANGED,

    // ── System ───────────────────────────────────────────────────────────
    SYSTEM_EVENT
}
