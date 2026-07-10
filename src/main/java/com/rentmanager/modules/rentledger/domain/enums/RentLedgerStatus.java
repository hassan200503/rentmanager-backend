package com.rentmanager.modules.rentledger.domain.enums;

/**
 * State machine for a {@code RentLedgerEntry}.
 *
 * <pre>
 * DUE ──payment covers full balance────────────► PAID
 * DUE ──partial payment──────────────────────────► PARTIALLY_PAID
 * DUE ──due_date + grace period elapses, no pay──► OVERDUE   (time-driven, not transaction-driven)
 *
 * PARTIALLY_PAID ──remaining balance paid────────► PAID
 * PARTIALLY_PAID ──grace period elapses──────────► OVERDUE
 *
 * OVERDUE ──payment received, covers balance─────► PAID
 * OVERDUE ──partial payment received─────────────► PARTIALLY_PAID
 *
 * (any of the above) ──payment total > amount_due──► OVERPAID
 * OVERPAID ──admin resolves (credit or refund)───► PAID
 * </pre>
 *
 * OVERDUE is deliberately not reachable purely from a transaction being
 * applied — it is set by {@code RentLedgerEntry#markOverdue()}, called by a
 * scheduler (Phase 4) comparing {@code today} against
 * {@code due_date + lease.gracePeriodDays}. OVERPAID is a held state
 * requiring an explicit admin resolution (not auto-carry-forward) — see
 * {@code RentLedgerEntry#resolveOverpaymentWithRefund(RentTransaction)} and
 * {@code RentLedgerEntry#resolveOverpaymentAsCredit()}.
 */
public enum RentLedgerStatus {

    DUE,
    PARTIALLY_PAID,
    OVERDUE,
    PAID,
    OVERPAID;

    public boolean isSettled() {
        return this == PAID;
    }

    public boolean isOutstanding() {
        return this == DUE || this == PARTIALLY_PAID || this == OVERDUE;
    }

    public boolean requiresAdminResolution() {
        return this == OVERPAID;
    }
}