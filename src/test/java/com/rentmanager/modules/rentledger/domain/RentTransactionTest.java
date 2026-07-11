package com.rentmanager.modules.rentledger.domain;

import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.shared.exception.ErrorCode;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentTransactionTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID ledgerEntryId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 5, 10, 0);

    private RentTransaction validTransaction(RentTransactionType type, BigDecimal amount) {
        return RentTransaction.create(
                tenantId, ledgerEntryId, leaseId, type, amount,
                null, RentTransactionSource.CASH, "admin-1", occurredAt
        );
    }

    @Nested
    class Create {

        @Test
        void succeedsWithValidArguments() {
            RentTransaction tx = validTransaction(RentTransactionType.PAYMENT, new BigDecimal("100.00"));

            assertThat(tx.getId()).isNotNull();
            assertThat(tx.getTenantId()).isEqualTo(tenantId);
            assertThat(tx.getLedgerEntryId()).isEqualTo(ledgerEntryId);
            assertThat(tx.getLeaseId()).isEqualTo(leaseId);
            assertThat(tx.getType()).isEqualTo(RentTransactionType.PAYMENT);
            assertThat(tx.getRecordedBy()).isEqualTo("admin-1");
            assertThat(tx.getOccurredAt()).isEqualTo(occurredAt);
            // version/createdAt/updatedAt intentionally unset until first persist
            assertThat(tx.getVersion()).isNull();
            assertThat(tx.getCreatedAt()).isNull();
            assertThat(tx.getUpdatedAt()).isNull();
        }

        @Test
        void scalesAmountToTwoDecimalPlaces() {
            RentTransaction tx = validTransaction(RentTransactionType.PAYMENT, new BigDecimal("100"));

            assertThat(tx.getAmount()).isEqualByComparingTo("100.00");
            assertThat(tx.getAmount().scale()).isEqualTo(2);
        }

        @Test
        void rejectsNullTenantId() {
            assertThatThrownBy(() -> RentTransaction.create(
                    null, ledgerEntryId, leaseId, RentTransactionType.PAYMENT, BigDecimal.TEN,
                    null, RentTransactionSource.CASH, "admin-1", occurredAt
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_TENANT_NULL);
        }

        @Test
        void rejectsNullLedgerEntryId() {
            assertThatThrownBy(() -> RentTransaction.create(
                    tenantId, null, leaseId, RentTransactionType.PAYMENT, BigDecimal.TEN,
                    null, RentTransactionSource.CASH, "admin-1", occurredAt
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_LEDGER_ENTRY_NULL);
        }

        @Test
        void rejectsNullLeaseId() {
            assertThatThrownBy(() -> RentTransaction.create(
                    tenantId, ledgerEntryId, null, RentTransactionType.PAYMENT, BigDecimal.TEN,
                    null, RentTransactionSource.CASH, "admin-1", occurredAt
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_LEASE_NULL);
        }

        @Test
        void rejectsNullType() {
            assertThatThrownBy(() -> RentTransaction.create(
                    tenantId, ledgerEntryId, leaseId, null, BigDecimal.TEN,
                    null, RentTransactionSource.CASH, "admin-1", occurredAt
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_TYPE_NULL);
        }

        @Test
        void rejectsNullAmount() {
            assertThatThrownBy(() -> RentTransaction.create(
                    tenantId, ledgerEntryId, leaseId, RentTransactionType.PAYMENT, null,
                    null, RentTransactionSource.CASH, "admin-1", occurredAt
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT);
        }

        @Test
        void rejectsZeroAmount() {
            assertThatThrownBy(() -> validTransaction(RentTransactionType.PAYMENT, BigDecimal.ZERO))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT);
        }

        @Test
        void rejectsNegativeAmount() {
            assertThatThrownBy(() -> validTransaction(RentTransactionType.PAYMENT, new BigDecimal("-5.00")))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT);
        }

        @Test
        void rejectsNullSource() {
            assertThatThrownBy(() -> RentTransaction.create(
                    tenantId, ledgerEntryId, leaseId, RentTransactionType.PAYMENT, BigDecimal.TEN,
                    null, null, "admin-1", occurredAt
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_SOURCE_NULL);
        }

        @Test
        void rejectsBlankRecordedBy() {
            assertThatThrownBy(() -> RentTransaction.create(
                    tenantId, ledgerEntryId, leaseId, RentTransactionType.PAYMENT, BigDecimal.TEN,
                    null, RentTransactionSource.CASH, "   ", occurredAt
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_RECORDED_BY_REQUIRED);
        }

        @Test
        void rejectsNullOccurredAt() {
            assertThatThrownBy(() -> RentTransaction.create(
                    tenantId, ledgerEntryId, leaseId, RentTransactionType.PAYMENT, BigDecimal.TEN,
                    null, RentTransactionSource.CASH, "admin-1", null
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_OCCURRED_AT_NULL);
        }

        @Test
        void allowsNullExternalReference() {
            // CASH/ADMIN_ADJUSTMENT sources legitimately have no external reference — not a
            // domain invariant of the transaction itself, per the class javadoc.
            RentTransaction tx = validTransaction(RentTransactionType.WAIVER, BigDecimal.TEN);
            assertThat(tx.getExternalReference()).isNull();
        }
    }

    @Nested
    class BalanceDirectionClassification {

        @Test
        void rentChargeIncreasesOnly() {
            RentTransaction tx = validTransaction(RentTransactionType.RENT_CHARGE, BigDecimal.TEN);
            assertThat(tx.increasesBalanceOwed()).isTrue();
            assertThat(tx.reducesBalanceOwed()).isFalse();
        }

        @Test
        void paymentReducesOnly() {
            RentTransaction tx = validTransaction(RentTransactionType.PAYMENT, BigDecimal.TEN);
            assertThat(tx.increasesBalanceOwed()).isFalse();
            assertThat(tx.reducesBalanceOwed()).isTrue();
        }

        @Test
        void waiverReducesOnly() {
            RentTransaction tx = validTransaction(RentTransactionType.WAIVER, BigDecimal.TEN);
            assertThat(tx.increasesBalanceOwed()).isFalse();
            assertThat(tx.reducesBalanceOwed()).isTrue();
        }

        @Test
        void creditAppliedReducesOnly() {
            RentTransaction tx = validTransaction(RentTransactionType.CREDIT_APPLIED, BigDecimal.TEN);
            assertThat(tx.increasesBalanceOwed()).isFalse();
            assertThat(tx.reducesBalanceOwed()).isTrue();
        }

        @Test
        void refundIsExcludedFromBothHelpers() {
            // Deliberate: REFUND reduces amountPaid, not balance owed — it's handled via
            // resolveOverpaymentWithRefund(), never through the generic apply-transaction path.
            RentTransaction tx = validTransaction(RentTransactionType.REFUND, BigDecimal.TEN);
            assertThat(tx.increasesBalanceOwed()).isFalse();
            assertThat(tx.reducesBalanceOwed()).isFalse();
        }

        @Test
        void adjustmentIsExcludedFromBothHelpers() {
            // Deliberate: ADJUSTMENT can move amountDue either way and is handled explicitly
            // by RentLedgerEntry.applyAdjustment(), not through either generic helper.
            RentTransaction tx = validTransaction(RentTransactionType.ADJUSTMENT, BigDecimal.TEN);
            assertThat(tx.increasesBalanceOwed()).isFalse();
            assertThat(tx.reducesBalanceOwed()).isFalse();
        }
    }

    @Nested
    class Rehydrate {

        @Test
        void restoresAllFieldsExactly() {
            UUID id = UUID.randomUUID();
            Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
            Instant updatedAt = Instant.parse("2026-01-02T00:00:00Z");

            RentTransaction tx = RentTransaction.rehydrate(
                    id, tenantId, ledgerEntryId, leaseId, RentTransactionType.PAYMENT,
                    new BigDecimal("250.00"), "MPESA-REF-1", RentTransactionSource.MPESA,
                    "admin-1", occurredAt, 3L, createdAt, updatedAt
            );

            assertThat(tx.getId()).isEqualTo(id);
            assertThat(tx.getTenantId()).isEqualTo(tenantId);
            assertThat(tx.getExternalReference()).isEqualTo("MPESA-REF-1");
            assertThat(tx.getVersion()).isEqualTo(3L);
            assertThat(tx.getCreatedAt()).isEqualTo(createdAt);
            assertThat(tx.getUpdatedAt()).isEqualTo(updatedAt);
        }
    }
}