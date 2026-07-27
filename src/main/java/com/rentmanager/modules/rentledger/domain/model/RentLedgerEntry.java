package com.rentmanager.modules.rentledger.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.events.RentDuePosted;
import com.rentmanager.modules.rentledger.domain.events.RentOverdueDetected;
import com.rentmanager.modules.rentledger.domain.events.RentOverpaymentDetected;
import com.rentmanager.modules.rentledger.domain.events.RentPaymentApplied;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Derived summary of all {@link RentTransaction}s posted against one
 * lease's billing period. Kept in sync with its transactions in the same
 * DB transaction they're inserted in (the "hybrid" model: fast dashboard
 * reads off this row, full audit trail underneath in RentTransaction) —
 * this class never recomputes itself from scratch by replaying the
 * transaction log; each apply-method updates {@code amountPaid} and
 * {@code status} incrementally as the corresponding RentTransaction is
 * created, and registers the domain event that corresponds to that change.
 *
 * {@code amountDue} is fixed at creation and never recomputed even if
 * proration rules change later (see class-level design doc) — the one
 * documented exception is {@link #applyAdjustment}, which is the explicit,
 * audited path for correcting it after the fact.
 *
 * {@code createdAt}/{@code updatedAt} are {@link Instant} (not
 * {@link LocalDateTime}) to match {@code BaseEntity}'s audit-timestamp
 * contract — a fixed, timezone-agnostic point on the UTC timeline, which
 * matters for a multi-tenant system where tenants span timezones.
 *
 * DESIGN DECISIONS made while unblocking this class (flagging per your
 * ground rules rather than treating them as obviously correct):
 *
 * 1. Idempotent markOverdue(): if the entry is already OVERDUE, this is a
 *    no-op rather than a thrown exception, so a scheduler (Phase 4) that
 *    re-runs and re-evaluates entries it already flagged doesn't have to
 *    special-case "already handled". Calling it on a PAID/OVERPAID entry
 *    IS an error — a scheduler should only ever call this on entries where
 *    isOutstanding() is true, so hitting a settled entry here means the
 *    caller's filtering is broken, not that this method should tolerate it.
 *
 * 2. Overpayment resolution is split into two methods, not one
 *    resolveOverpayment(resolution) taking an enum, because the two paths
 *    have genuinely different signatures: REFUND requires a RentTransaction
 *    (money is actually moving, so it must be recorded), APPLY_AS_CREDIT
 *    does not touch this entry's amountPaid at all — the actual
 *    CREDIT_APPLIED transaction is posted on a DIFFERENT (future) entry by
 *    the application service, and calling resolveOverpaymentAsCredit() here
 *    just marks this entry's excess as spoken-for so it stops showing as
 *    OVERPAID. Collapsing these into one method with an enum param would
 *    hide that asymmetry rather than express it.
 *
 * 3. applyTransaction(...) explicitly rejects RENT_CHARGE and ADJUSTMENT
 *    transaction types — RENT_CHARGE is represented directly by amountDue
 *    at creation (see create()) and must never be applied a second time;
 *    ADJUSTMENT has its own method (applyAdjustment) because it can move
 *    amountDue in either direction, which is a fundamentally different
 *    operation than reducing balance owed.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RentLedgerEntry extends AggregateRoot {

    private UUID leaseId;
    private UUID unitId;
    private UUID tenantProfileId;
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private LocalDate dueDate;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private RentLedgerStatus status;
    private boolean prorated;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    // ------------------------------------------------------------------
    // Creation
    // ------------------------------------------------------------------

    /**
     * Creates a new ledger entry in DUE status and registers RentDuePosted.
     * Does NOT create the corresponding RENT_CHARGE RentTransaction — per
     * the locked design, that row is created by the application service in
     * the same DB transaction as this entity's insert (RentLedgerApplicationService
     * .postCharge(...)), so the two aggregates land atomically without this
     * domain method reaching across aggregate boundaries to construct one.
     */
    public static RentLedgerEntry create(
            UUID tenantId,
            String correlationId,
            UUID leaseId,
            UUID unitId,
            UUID tenantProfileId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd,
            LocalDate dueDate,
            BigDecimal amountDue,
            boolean prorated
    ) {
        if (tenantId == null) {
            throw new RentLedgerStateException("tenantId cannot be null", ErrorCode.RENT_LEDGER_ENTRY_TENANT_NULL);
        }
        if (leaseId == null) {
            throw new RentLedgerStateException("leaseId cannot be null", ErrorCode.RENT_LEDGER_ENTRY_LEASE_NULL);
        }
        if (unitId == null) {
            throw new RentLedgerStateException("unitId cannot be null", ErrorCode.RENT_LEDGER_ENTRY_UNIT_NULL);
        }
        if (tenantProfileId == null) {
            throw new RentLedgerStateException("tenantProfileId cannot be null", ErrorCode.RENT_LEDGER_ENTRY_TENANT_PROFILE_NULL);
        }
        if (billingPeriodStart == null || billingPeriodEnd == null) {
            throw new RentLedgerStateException("billing period dates cannot be null", ErrorCode.RENT_LEDGER_ENTRY_PERIOD_NULL);
        }
        if (!billingPeriodEnd.isAfter(billingPeriodStart)) {
            throw new RentLedgerStateException("billingPeriodEnd must be after billingPeriodStart", ErrorCode.RENT_LEDGER_ENTRY_PERIOD_INVALID);
        }
        if (dueDate == null) {
            throw new RentLedgerStateException("dueDate cannot be null", ErrorCode.RENT_LEDGER_ENTRY_DUE_DATE_NULL);
        }
        if (amountDue == null || amountDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RentLedgerStateException("amountDue must be > 0", ErrorCode.RENT_LEDGER_ENTRY_INVALID_AMOUNT_DUE);
        }

        Instant now = Instant.now();

        RentLedgerEntry entry = RentLedgerEntry.builder()
                .leaseId(leaseId)
                .unitId(unitId)
                .tenantProfileId(tenantProfileId)
                .billingPeriodStart(billingPeriodStart)
                .billingPeriodEnd(billingPeriodEnd)
                .dueDate(dueDate)
                .amountDue(amountDue.setScale(2, java.math.RoundingMode.HALF_UP))
                .amountPaid(BigDecimal.ZERO.setScale(2))
                .status(RentLedgerStatus.DUE)
                .prorated(prorated)
                .version(0L)
                .createdAt(now)
                .updatedAt(now)
                .build();

        entry.setId(UUID.randomUUID());
        entry.assignTenant(tenantId);

        entry.registerEvent(new RentDuePosted(
                tenantId,
                entry.getId(),
                correlationId,
                leaseId,
                unitId,
                tenantProfileId,
                entry.getAmountDue(),
                dueDate
        ));

        return entry;
    }

    public static RentLedgerEntry rehydrate(
            UUID id,
            UUID tenantId,
            UUID leaseId,
            UUID unitId,
            UUID tenantProfileId,
            LocalDate billingPeriodStart,
            LocalDate billingPeriodEnd,
            LocalDate dueDate,
            BigDecimal amountDue,
            BigDecimal amountPaid,
            RentLedgerStatus status,
            boolean prorated,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        RentLedgerEntry entry = RentLedgerEntry.builder()
                .leaseId(leaseId)
                .unitId(unitId)
                .tenantProfileId(tenantProfileId)
                .billingPeriodStart(billingPeriodStart)
                .billingPeriodEnd(billingPeriodEnd)
                .dueDate(dueDate)
                .amountDue(amountDue)
                .amountPaid(amountPaid)
                .status(status)
                .prorated(prorated)
                .version(version)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        entry.setId(id);
        entry.assignTenant(tenantId);
        return entry;
    }

    // ------------------------------------------------------------------
    // Applying transactions that reduce the balance owed
    // ------------------------------------------------------------------

    /**
     * Applies a PAYMENT, WAIVER, or CREDIT_APPLIED transaction: increases
     * amountPaid, recomputes status, and registers RentPaymentApplied
     * (plus RentOverpaymentDetected if this pushes the entry into OVERPAID
     * for the first time).
     *
     * The caller (application service) is responsible for having already
     * persisted / validated the RentTransaction itself — this method only
     * interprets it. transaction.getLedgerEntryId() must match this
     * entry's id, which is checked here rather than trusted, since a
     * mismatched id would silently corrupt the wrong entry's balance.
     */
    public void applyTransaction(String correlationId, RentTransaction transaction) {
        if (transaction.getType() != RentTransactionType.REFUND) {
            requireNotSettledOrOverpaid();
        }
        requireMatchingLedgerEntry(transaction);

        if (!transaction.reducesBalanceOwed()) {
            throw new RentLedgerStateException(
                    "applyTransaction only accepts PAYMENT, WAIVER, CREDIT_APPLIED, DEPOSIT, or REFUND; got " + transaction.getType(),
                    ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE
            );
        }

        if (transaction.getType() == RentTransactionType.REFUND) {
            this.amountPaid = this.amountPaid.subtract(transaction.getAmount());
            if (this.amountPaid.compareTo(BigDecimal.ZERO) < 0) {
                this.amountPaid = BigDecimal.ZERO.setScale(2);
            }
        } else {
            this.amountPaid = this.amountPaid.add(transaction.getAmount());
        }
        this.updatedAt = Instant.now();

        boolean wasAlreadyOverpaid = this.status == RentLedgerStatus.OVERPAID;
        recomputeStatus();

        registerEvent(new RentPaymentApplied(
                getTenantId(),
                getId(),
                correlationId,
                leaseId,
                transaction.getId(),
                transaction.getAmount(),
                this.status
        ));

        if (this.status == RentLedgerStatus.OVERPAID && !wasAlreadyOverpaid) {
            registerEvent(new RentOverpaymentDetected(
                    getTenantId(),
                    getId(),
                    correlationId,
                    leaseId,
                    getExcessAmount()
            ));
        }
    }

    // ------------------------------------------------------------------
    // Adjustments to the charge itself
    // ------------------------------------------------------------------

    /**
     * Applies an ADJUSTMENT transaction that moves amountDue itself, either
     * up or down (e.g. correcting a mis-posted charge). {@code delta} is
     * signed: positive increases amountDue, negative decreases it.
     * transaction.getAmount() is always positive per RentTransaction's
     * contract — the caller passes the signed delta separately here because
     * RentTransaction itself has no opinion on ADJUSTMENT's direction (see
     * RentTransaction#increasesBalanceOwed/reducesBalanceOwed javadoc).
     */
    public void applyAdjustment(String correlationId, RentTransaction transaction, BigDecimal delta) {
        requireNotSettledOrOverpaid();
        requireMatchingLedgerEntry(transaction);

        if (transaction.getType() != RentTransactionType.ADJUSTMENT) {
            throw new RentLedgerStateException(
                    "applyAdjustment only accepts ADJUSTMENT transactions; got " + transaction.getType(),
                    ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE
            );
        }
        if (delta == null || delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new RentLedgerStateException("delta must be non-zero", ErrorCode.RENT_LEDGER_ENTRY_INVALID_ADJUSTMENT);
        }

        BigDecimal newAmountDue = this.amountDue.add(delta);
        if (newAmountDue.compareTo(BigDecimal.ZERO) < 0) {
            throw new RentLedgerStateException("adjustment cannot reduce amountDue below zero", ErrorCode.RENT_LEDGER_ENTRY_INVALID_ADJUSTMENT);
        }

        this.amountDue = newAmountDue.setScale(2, java.math.RoundingMode.HALF_UP);
        this.updatedAt = Instant.now();

        boolean wasAlreadyOverpaid = this.status == RentLedgerStatus.OVERPAID;
        recomputeStatus();

        if (this.status == RentLedgerStatus.OVERPAID && !wasAlreadyOverpaid) {
            registerEvent(new RentOverpaymentDetected(
                    getTenantId(),
                    getId(),
                    correlationId,
                    leaseId,
                    getExcessAmount()
            ));
        }
    }

    // ------------------------------------------------------------------
    // Time-driven transition
    // ------------------------------------------------------------------

    /**
     * Transitions DUE or PARTIALLY_PAID to OVERDUE. Idempotent when already
     * OVERDUE (no-op, no duplicate event) so a re-running scheduler doesn't
     * need to pre-filter already-flagged entries. Throws if called on a
     * settled (PAID) or held (OVERPAID) entry — a scheduler should only
     * invoke this on entries where isOutstanding() is true, so reaching
     * this branch means the caller's own filtering is broken.
     */
    public void markOverdue(String correlationId, int daysOverdue) {
        if (this.status == RentLedgerStatus.OVERDUE) {
            return;
        }
        if (this.status != RentLedgerStatus.DUE && this.status != RentLedgerStatus.PARTIALLY_PAID) {
            throw new RentLedgerStateException(
                    "markOverdue can only be called from DUE or PARTIALLY_PAID, was " + this.status,
                    ErrorCode.RENT_LEDGER_ENTRY_ILLEGAL_TRANSITION
            );
        }
        if (daysOverdue <= 0) {
            throw new RentLedgerStateException("daysOverdue must be > 0", ErrorCode.RENT_LEDGER_ENTRY_INVALID_DAYS_OVERDUE);
        }

        this.status = RentLedgerStatus.OVERDUE;
        this.updatedAt = Instant.now();

        registerEvent(new RentOverdueDetected(
                getTenantId(),
                getId(),
                correlationId,
                leaseId,
                tenantProfileId,
                daysOverdue
        ));
    }

    // ------------------------------------------------------------------
    // Overpayment resolution (admin-driven, held state)
    // ------------------------------------------------------------------

    /**
     * Resolves an OVERPAID entry by refunding the excess: reduces
     * amountPaid by the refund transaction's amount and transitions to
     * PAID. The application service is responsible for actually disbursing
     * the refund (Phase 5) and for constructing the REFUND RentTransaction
     * beforehand — this method only records the ledger-side consequence
     * and fires no further event, since RentPaymentApplied is specifically
     * about balance-reducing transactions and a refund is the opposite of
     * that; the state change here (OVERPAID → PAID) is visible to any
     * consumer that reads status off this aggregate directly.
     */
    public void resolveOverpaymentWithRefund(RentTransaction refundTransaction) {
        if (this.status != RentLedgerStatus.OVERPAID) {
            throw new RentLedgerStateException(
                    "resolveOverpaymentWithRefund can only be called on an OVERPAID entry, was " + this.status,
                    ErrorCode.RENT_LEDGER_ENTRY_ILLEGAL_TRANSITION
            );
        }
        requireMatchingLedgerEntry(refundTransaction);
        if (refundTransaction.getType() != RentTransactionType.REFUND) {
            throw new RentLedgerStateException(
                    "resolveOverpaymentWithRefund requires a REFUND transaction; got " + refundTransaction.getType(),
                    ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE
            );
        }

        BigDecimal resultingAmountPaid = this.amountPaid.subtract(refundTransaction.getAmount());
        if (resultingAmountPaid.compareTo(this.amountDue) != 0) {
            throw new RentLedgerStateException(
                    "refund amount must exactly resolve the excess (amountPaid - refund must equal amountDue)",
                    ErrorCode.RENT_LEDGER_ENTRY_INVALID_REFUND_AMOUNT
            );
        }

        this.amountPaid = resultingAmountPaid;
        this.status = RentLedgerStatus.PAID;
        this.updatedAt = Instant.now();
    }

    /**
     * Resolves an OVERPAID entry by marking its excess as applied to a
     * different (future) period's entry. Deliberately does NOT touch
     * amountPaid here — the actual CREDIT_APPLIED RentTransaction is posted
     * on the OTHER entry by the application service via that entry's own
     * applyTransaction(...); this method only closes out THIS entry so it
     * stops surfacing as requiring admin resolution.
     */
    public void resolveOverpaymentAsCredit() {
        if (this.status != RentLedgerStatus.OVERPAID) {
            throw new RentLedgerStateException(
                    "resolveOverpaymentAsCredit can only be called on an OVERPAID entry, was " + this.status,
                    ErrorCode.RENT_LEDGER_ENTRY_ILLEGAL_TRANSITION
            );
        }
        this.status = RentLedgerStatus.PAID;
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------------
    // Transaction reversal (delete support)
    // ------------------------------------------------------------------

    /**
     * Reverses the effect of a previously-applied transaction when it is
     * permanently deleted from the system. No domain event is registered
     * — we are correcting history, not applying new business state.
     *
     * RENT_CHARGE cannot be removed (it is the entry's foundational charge;
     * delete the entire entry instead). ADJUSTMENT cannot be removed
     * because the direction (delta sign) is not stored on the transaction
     * record — only {@code delta.abs()} is persisted.
     */
    public void removeTransaction(RentTransaction transaction) {
        requireMatchingLedgerEntry(transaction);

        switch (transaction.getType()) {
            case PAYMENT:
            case WAIVER:
            case CREDIT_APPLIED:
            case DEPOSIT:
                this.amountPaid = this.amountPaid.subtract(transaction.getAmount());
                if (this.amountPaid.compareTo(BigDecimal.ZERO) < 0) {
                    this.amountPaid = BigDecimal.ZERO.setScale(2);
                }
                break;
            case REFUND:
                this.amountPaid = this.amountPaid.add(transaction.getAmount());
                break;
            case RENT_CHARGE:
                throw new RentLedgerStateException(
                        "Cannot remove RENT_CHARGE transaction; delete the entire entry instead",
                        ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE
                );
            case ADJUSTMENT:
                throw new RentLedgerStateException(
                        "Cannot remove ADJUSTMENT transaction; direction (signed delta) is not stored",
                        ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE
                );
        }

        this.updatedAt = Instant.now();
        recomputeStatus();
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public BigDecimal getExcessAmount() {
        BigDecimal excess = this.amountPaid.subtract(this.amountDue);
        return excess.compareTo(BigDecimal.ZERO) > 0 ? excess : BigDecimal.ZERO;
    }

    public BigDecimal getBalanceOwed() {
        BigDecimal balance = this.amountDue.subtract(this.amountPaid);
        return balance.compareTo(BigDecimal.ZERO) > 0 ? balance : BigDecimal.ZERO;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private void recomputeStatus() {
        int comparison = this.amountPaid.compareTo(this.amountDue);

        if (comparison > 0) {
            this.status = RentLedgerStatus.OVERPAID;
        } else if (comparison == 0) {
            this.status = RentLedgerStatus.PAID;
        } else if (this.amountPaid.compareTo(BigDecimal.ZERO) > 0) {
            this.status = RentLedgerStatus.PARTIALLY_PAID;
        } else {
            // amountPaid == 0: preserve OVERDUE rather than reverting to
            // DUE. This branch is only reachable via applyAdjustment
            // increasing amountDue back above a zero amountPaid; a normal
            // applyTransaction call never decreases amountPaid, so it can't
            // land here with a non-zero starting balance.
            this.status = (this.status == RentLedgerStatus.OVERDUE)
                    ? RentLedgerStatus.OVERDUE
                    : RentLedgerStatus.DUE;
        }
    }

    private void requireNotSettledOrOverpaid() {
        if (this.status == RentLedgerStatus.PAID) {
            throw new RentLedgerStateException("cannot modify a PAID entry", ErrorCode.RENT_LEDGER_ENTRY_ALREADY_SETTLED);
        }
        if (this.status == RentLedgerStatus.OVERPAID) {
            throw new RentLedgerStateException(
                    "entry is OVERPAID and requires admin resolution before further transactions can apply",
                    ErrorCode.RENT_LEDGER_ENTRY_REQUIRES_RESOLUTION
            );
        }
    }

    private void requireMatchingLedgerEntry(RentTransaction transaction) {
        if (transaction == null) {
            throw new RentLedgerStateException("transaction cannot be null", ErrorCode.RENT_LEDGER_ENTRY_TRANSACTION_NULL);
        }
        if (!this.getId().equals(transaction.getLedgerEntryId())) {
            throw new RentLedgerStateException(
                    "transaction.ledgerEntryId does not match this entry's id",
                    ErrorCode.RENT_LEDGER_ENTRY_TRANSACTION_MISMATCH
            );
        }
    }
}