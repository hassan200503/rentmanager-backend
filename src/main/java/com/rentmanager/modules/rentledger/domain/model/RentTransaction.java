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
 * {@code version}, {@code createdAt}, and {@code updatedAt} are populated
 * only on {@link #rehydrate}, never on {@link #create}. A freshly created
 * transaction has no persisted version or timestamps yet — those are
 * assigned by the persistence layer on first save, mirroring how
 * {@code RentLedgerEntry} handles the same three fields.
 *
 * {@code createdAt}/{@code updatedAt} are {@link Instant} (not
 * {@link LocalDateTime}) to match {@code BaseEntity}'s audit-timestamp
 * contract — a fixed, timezone-agnostic point on the UTC timeline, which
 * matters for a multi-tenant system where tenants span timezones.
 * {@code occurredAt} remains a {@code LocalDateTime}: it's a
 * business-domain timestamp supplied by the recorder, not an audit field.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class RentTransaction extends AggregateRoot {

    private UUID ledgerEntryId;
    private UUID leaseId;
    private RentTransactionType type;
    private BigDecimal amount;
    private String externalReference;
    private RentTransactionSource source;
    private String recordedBy;
    private LocalDateTime occurredAt;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;

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

        RentTransaction transaction = RentTransaction.builder()
                .ledgerEntryId(ledgerEntryId)
                .leaseId(leaseId)
                .type(type)
                .amount(amount.setScale(2, java.math.RoundingMode.HALF_UP))
                .externalReference(externalReference)
                .source(source)
                .recordedBy(recordedBy)
                .occurredAt(occurredAt)
                // version, createdAt, updatedAt intentionally left unset
                // (null) here — this is a brand-new, not-yet-persisted
                // transaction. The persistence layer assigns these on
                // first save; see class javadoc.
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
                .version(version)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        transaction.setId(id);
        transaction.assignTenant(tenantId);
        return transaction;
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
     */
    public boolean reducesBalanceOwed() {
        return type == RentTransactionType.PAYMENT
                || type == RentTransactionType.WAIVER
                || type == RentTransactionType.CREDIT_APPLIED;
        // REFUND deliberately excluded: a refund reduces amountPaid (money
        // handed back), not the balance owed — it's the mechanism for
        // resolving an OVERPAID entry, applied via a dedicated method on
        // RentLedgerEntry rather than the generic apply-transaction path.
    }
}