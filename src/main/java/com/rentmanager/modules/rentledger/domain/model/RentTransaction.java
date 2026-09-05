package com.rentmanager.modules.rentledger.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only record of a single movement of money against a
 * {@link RentLedgerEntry}. Never updated, never deleted, once persisted.
 *
 * {@code amount} is always positive; direction is implied entirely by
 * {@link RentTransactionType} — see that enum's javadoc for which types
 * increase vs. reduce the balance owed. This class does not decide that;
 * the owning {@code RentLedgerEntry} interprets it when recomputing
 * {@code amountPaid}/{@code status}, keeping the interpretation logic in
 * one place rather than duplicated across every writer.
 *
 * This aggregate does not itself register domain events — the events that
 * matter to the rest of the system ({@code RentPaymentApplied},
 * {@code RentOverpaymentDetected}, etc.) are semantically about the ledger
 * entry's state changing, not about the transaction row existing, so they
 * are registered by {@link RentLedgerEntry} in the same application-service
 * call that creates this transaction.
 *
 * FIX (this session): declares its own {@code version}/{@code createdAt}/
 * {@code updatedAt} fields, shadowing the ones inherited from
 * {@code BaseEntity}, mirroring the pattern already established on
 * {@link RentLedgerEntry}. Without this, two problems existed:
 * (1) there was no legal way to set {@code createdAt}/{@code updatedAt} on
 * rehydration, since {@code BaseEntity} exposes no public setter for
 * either; (2) {@code BaseEntity.getCreatedAt()}/{@code getUpdatedAt()}
 * silently fall back to {@code Instant.now()} when null instead of
 * returning null, which is wrong for a domain object that hasn't been
 * persisted yet and doesn't have a real timestamp assigned.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RentTransaction extends AggregateRoot {

    // Kenya-first default, matching CreateTenantCommandHandler.DEFAULT_CURRENCY
    // — used when a caller doesn't pass an explicit currency (see the
    // shorter create() overload below).
    private static final String DEFAULT_CURRENCY = "KES";

    private UUID ledgerEntryId;
    private UUID leaseId;
    private RentTransactionType type;
    private BigDecimal amount;
    private String externalReference;
    private RentTransactionSource source;
    private String recordedBy;
    private LocalDateTime occurredAt;

    // Populated only for type REVERSAL: the id of the transaction this one
    // voids. Null for every other type. See RentTransactionType#REVERSAL.
    private UUID reversesTransactionId;

    private String currency;

    // Commission snapshot — populated only for MPESA rent payments.
    // Null for all other transaction types (CASH, ADMIN_ADJUSTMENT, etc.).
    private BigDecimal commissionRatePercent;
    private BigDecimal commissionAmount;
    private BigDecimal netAmount;

    // Shadows BaseEntity's fields — see class javadoc FIX note.
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Shorter overload defaulting {@code currency} to {@link #DEFAULT_CURRENCY}
     * — most callers (including the bulk of this class's own test suite)
     * don't need to think about currency. Real money-posting call sites in
     * RentLedgerApplicationService use the currency-explicit overload below,
     * always passing the parent RentLedgerEntry's own currency so every
     * transaction against an entry agrees with it.
     */
    public static RentTransaction create(
            UUID tenantId,
            UUID ledgerEntryId,
            UUID leaseId,
            RentTransactionType type,
            BigDecimal amount,
            String externalReference,
            RentTransactionSource source,
            String recordedBy,
            LocalDateTime occurredAt
    ) {
        return create(
                tenantId, ledgerEntryId, leaseId, type, amount, externalReference,
                source, recordedBy, occurredAt, DEFAULT_CURRENCY
        );
    }

    public static RentTransaction create(
            UUID tenantId,
            UUID ledgerEntryId,
            UUID leaseId,
            RentTransactionType type,
            BigDecimal amount,
            String externalReference,
            RentTransactionSource source,
            String recordedBy,
            LocalDateTime occurredAt,
            String currency
    ) {
        if (tenantId == null) {
            throw new RentLedgerStateException("tenantId cannot be null", ErrorCode.RENT_TRANSACTION_TENANT_NULL);
        }
        if (ledgerEntryId == null) {
            throw new RentLedgerStateException("ledgerEntryId cannot be null", ErrorCode.RENT_TRANSACTION_LEDGER_ENTRY_NULL);
        }
        if (leaseId == null) {
            throw new RentLedgerStateException("leaseId cannot be null", ErrorCode.RENT_TRANSACTION_LEASE_NULL);
        }
        if (type == null) {
            throw new RentLedgerStateException("type cannot be null", ErrorCode.RENT_TRANSACTION_TYPE_NULL);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            // Always positive by contract — see class javadoc. A zero or
            // negative amount here almost always means a caller bug (e.g.
            // accidentally passing a signed delta instead of a magnitude),
            // so this fails loudly rather than silently accepting it.
            throw new RentLedgerStateException("amount must be > 0", ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT);
        }
        if (source == null) {
            throw new RentLedgerStateException("source cannot be null", ErrorCode.RENT_TRANSACTION_SOURCE_NULL);
        }
        if (recordedBy == null || recordedBy.isBlank()) {
            throw new RentLedgerStateException("recordedBy cannot be blank", ErrorCode.RENT_TRANSACTION_RECORDED_BY_REQUIRED);
        }
        if (occurredAt == null) {
            throw new RentLedgerStateException("occurredAt cannot be null", ErrorCode.RENT_TRANSACTION_OCCURRED_AT_NULL);
        }

        // externalReference is intentionally NOT validated as required here.
        // CASH and ADMIN_ADJUSTMENT sources legitimately have no external
        // reference; MPESA sources should always carry one, but that is an
        // application-layer concern (the calling service knows its source),
        // not a domain invariant of the transaction itself.

        // version/createdAt/updatedAt intentionally left unset (null) here
        // — they are only meaningful once this row has actually been
        // persisted. Set explicitly by rehydrate() when restoring a
        // previously-persisted transaction from the DB.
        RentTransaction transaction = RentTransaction.builder()
                .ledgerEntryId(ledgerEntryId)
                .leaseId(leaseId)
                .type(type)
                .amount(amount.setScale(2, java.math.RoundingMode.HALF_UP))
                .externalReference(externalReference)
                .source(source)
                .recordedBy(recordedBy)
                .occurredAt(occurredAt)
                .currency(currency != null ? currency : DEFAULT_CURRENCY)
                .build();

        transaction.setId(UUID.randomUUID());
        transaction.assignTenant(tenantId);

        return transaction;
    }

    public static RentTransaction rehydrate(
            UUID id,
            UUID tenantId,
            UUID ledgerEntryId,
            UUID leaseId,
            RentTransactionType type,
            BigDecimal amount,
            String externalReference,
            RentTransactionSource source,
            String recordedBy,
            LocalDateTime occurredAt,
            BigDecimal commissionRatePercent,
            BigDecimal commissionAmount,
            BigDecimal netAmount,
            UUID reversesTransactionId,
            String currency,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        RentTransaction transaction = RentTransaction.builder()
                .ledgerEntryId(ledgerEntryId)
                .leaseId(leaseId)
                .type(type)
                .amount(amount)
                .externalReference(externalReference)
                .source(source)
                .recordedBy(recordedBy)
                .occurredAt(occurredAt)
                .commissionRatePercent(commissionRatePercent)
                .commissionAmount(commissionAmount)
                .netAmount(netAmount)
                .reversesTransactionId(reversesTransactionId)
                .currency(currency != null ? currency : DEFAULT_CURRENCY)
                .version(version)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        transaction.setId(id);
        transaction.assignTenant(tenantId);
        return transaction;
    }

    /**
     * Creates the compensating REVERSAL transaction for {@code original}.
     * Deliberately does not carry over {@code original}'s external
     * reference — leaving it only on the original row is what keeps
     * {@code uk_rent_transactions_tenant_external_reference} (the M-Pesa
     * duplicate-callback guard) blocking replay of that receipt even after
     * it's been reversed.
     */
    public static RentTransaction reversalOf(
            RentTransaction original,
            UUID tenantId,
            String recordedBy,
            LocalDateTime occurredAt
    ) {
        if (original == null) {
            throw new RentLedgerStateException("original transaction cannot be null", ErrorCode.RENT_TRANSACTION_ORIGINAL_NULL);
        }

        RentTransaction reversal = RentTransaction.builder()
                .ledgerEntryId(original.getLedgerEntryId())
                .leaseId(original.getLeaseId())
                .type(RentTransactionType.REVERSAL)
                .amount(original.getAmount())
                .externalReference(null)
                .source(RentTransactionSource.ADMIN_ADJUSTMENT)
                .recordedBy(recordedBy)
                .occurredAt(occurredAt)
                .reversesTransactionId(original.getId())
                .currency(original.getCurrency())
                .build();

        reversal.setId(UUID.randomUUID());
        reversal.assignTenant(tenantId);
        return reversal;
    }

    public void applyCommission(BigDecimal commissionRatePercent, BigDecimal commissionAmount, BigDecimal netAmount) {
        this.commissionRatePercent = commissionRatePercent;
        this.commissionAmount = commissionAmount;
        this.netAmount = netAmount;
    }

    /**
     * True for transaction types that increase the balance owed on the
     * parent ledger entry. Used by {@code RentLedgerEntry} to recompute
     * {@code amountPaid} deterministically from its transaction list rather
     * than duplicating this classification in two places.
     */
    public boolean increasesBalanceOwed() {
        return type == RentTransactionType.RENT_CHARGE;
        // ADJUSTMENT can go either way and is handled explicitly by the
        // caller (RentLedgerEntry.applyAdjustment(...)) rather than through
        // this helper — see that method.
    }

    /**
     * True for transaction types that reduce the balance owed / count
     * toward amountPaid on the parent ledger entry.
     *
     * REFUND also returns true: although a refund reduces amountPaid
     * (money handed back) rather than increasing it, it still affects the
     * entry's balance and is applied through applyTransaction with
     * subtractive logic. The dedicated resolveOverpaymentWithRefund path
     * is used when an OVERPAID entry's exact excess is being resolved.
     *
     * DEPOSIT deliberately returns false: a security deposit is not rent
     * revenue and must not count toward amountPaid, or a lease's opening
     * rent charge could show as PAID/OVERPAID purely from deposit money
     * that was never actually rent. A DEPOSIT row still gets posted (see
     * RentLedgerApplicationService#postDeposit) as an audit/receipt entry
     * in the transaction history, but applyTransaction rejects it — the
     * held-deposit lifecycle (paid, refunded, forfeited) lives entirely in
     * the deposit module's own Deposit aggregate, which is the actual
     * system of record.
     */
    public boolean reducesBalanceOwed() {
        return type == RentTransactionType.PAYMENT
                || type == RentTransactionType.WAIVER
                || type == RentTransactionType.CREDIT_APPLIED
                || type == RentTransactionType.REFUND;
    }
}