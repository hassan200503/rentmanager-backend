package com.rentmanager.modules.rentledger.domain;

import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.events.RentOverdueDetected;
import com.rentmanager.modules.rentledger.domain.events.RentOverpaymentDetected;
import com.rentmanager.modules.rentledger.domain.events.RentPaymentApplied;
import com.rentmanager.modules.rentledger.domain.exception.RentLedgerStateException;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RentLedgerEntryTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();
    private final LocalDate periodStart = LocalDate.of(2026, 3, 1);
    private final LocalDate periodEnd = LocalDate.of(2026, 3, 31);
    private final LocalDate dueDate = LocalDate.of(2026, 3, 1);
    private final LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 5, 9, 0);

    private RentLedgerEntry entry;

    @BeforeEach
    void setUp() {
        entry = RentLedgerEntry.create(
                tenantId, "corr-1", leaseId, unitId, tenantProfileId,
                periodStart, periodEnd, dueDate, new BigDecimal("1000.00"), false
        );
        entry.pullDomainEvents(); // drain the RentDuePosted fired by create(), not under test here
    }

    private RentTransaction paymentOf(BigDecimal amount) {
        return RentTransaction.create(
                tenantId, entry.getId(), leaseId, RentTransactionType.PAYMENT, amount,
                null, RentTransactionSource.CASH, "admin-1", occurredAt
        );
    }

    @Nested
    class Create {

        @Test
        void firesRentDuePostedAndStartsInDueStatus() {
            RentLedgerEntry fresh = RentLedgerEntry.create(
                    tenantId, "corr-2", leaseId, unitId, tenantProfileId,
                    periodStart, periodEnd, dueDate, new BigDecimal("1000.00"), false
            );

            assertThat(fresh.getStatus()).isEqualTo(RentLedgerStatus.DUE);
            assertThat(fresh.getAmountPaid()).isEqualByComparingTo("0.00");
            assertThat(fresh.pullDomainEvents()).hasSize(1)
                    .first().isInstanceOf(com.rentmanager.modules.rentledger.domain.events.RentDuePosted.class);
        }

        @Test
        void rejectsInvertedBillingPeriod() {
            assertThatThrownBy(() -> RentLedgerEntry.create(
                    tenantId, "corr-3", leaseId, unitId, tenantProfileId,
                    periodEnd, periodStart, dueDate, new BigDecimal("1000.00"), false
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_PERIOD_INVALID);
        }

        @Test
        void rejectsZeroOrNegativeAmountDue() {
            assertThatThrownBy(() -> RentLedgerEntry.create(
                    tenantId, "corr-4", leaseId, unitId, tenantProfileId,
                    periodStart, periodEnd, dueDate, BigDecimal.ZERO, false
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_INVALID_AMOUNT_DUE);
        }
    }

    @Nested
    class ApplyTransaction {

        @Test
        void partialPaymentMovesToPartiallyPaidAndFiresEvent() {
            entry.applyTransaction("corr", paymentOf(new BigDecimal("400.00")));

            assertThat(entry.getStatus()).isEqualTo(RentLedgerStatus.PARTIALLY_PAID);
            assertThat(entry.getAmountPaid()).isEqualByComparingTo("400.00");
            assertThat(entry.pullDomainEvents()).hasSize(1)
                    .first().isInstanceOf(RentPaymentApplied.class);
        }

        @Test
        void exactPaymentMovesToPaid() {
            entry.applyTransaction("corr", paymentOf(new BigDecimal("1000.00")));

            assertThat(entry.getStatus()).isEqualTo(RentLedgerStatus.PAID);
        }

        @Test
        void overpaymentMovesToOverpaidAndFiresBothEvents() {
            entry.applyTransaction("corr", paymentOf(new BigDecimal("1200.00")));

            assertThat(entry.getStatus()).isEqualTo(RentLedgerStatus.OVERPAID);
            assertThat(entry.getExcessAmount()).isEqualByComparingTo("200.00");

            var events = entry.pullDomainEvents();
            assertThat(events).hasSize(2);
            assertThat(events.get(0)).isInstanceOf(RentPaymentApplied.class);
            assertThat(events.get(1)).isInstanceOf(RentOverpaymentDetected.class);
        }

        @Test
        void furtherTransactionsAreBlockedOnceOverpaid() {
            // This also demonstrates why RentOverpaymentDetected can only ever fire once per
            // episode: requireNotSettledOrOverpaid() rejects any further applyTransaction/
            // applyAdjustment call once OVERPAID, so the "wasAlreadyOverpaid" check inside
            // applyTransaction/applyAdjustment can never observe a true value in practice —
            // the guard at the top of each method already prevents reaching it a second time.
            entry.applyTransaction("corr", paymentOf(new BigDecimal("1200.00")));
            entry.pullDomainEvents();

            assertThatThrownBy(() -> entry.applyTransaction("corr", paymentOf(new BigDecimal("10.00"))))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_REQUIRES_RESOLUTION);
        }

        @Test
        void blockedOncePaid() {
            entry.applyTransaction("corr", paymentOf(new BigDecimal("1000.00")));

            assertThatThrownBy(() -> entry.applyTransaction("corr", paymentOf(new BigDecimal("10.00"))))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_ALREADY_SETTLED);
        }

        @Test
        void rejectsRentChargeType() {
            RentTransaction charge = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.RENT_CHARGE,
                    BigDecimal.TEN, null, RentTransactionSource.SYSTEM, "SYSTEM", occurredAt
            );

            assertThatThrownBy(() -> entry.applyTransaction("corr", charge))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE);
        }

        @Test
        void rejectsDepositType() {
            // A deposit is not rent revenue and must never move amountPaid —
            // see RentTransaction.reducesBalanceOwed()'s javadoc.
            RentTransaction deposit = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.DEPOSIT,
                    BigDecimal.TEN, null, RentTransactionSource.MPESA, "SYSTEM", occurredAt
            );

            assertThatThrownBy(() -> entry.applyTransaction("corr", deposit))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE);
        }

        @Test
        void rejectsMismatchedLedgerEntryId() {
            RentTransaction foreignTx = RentTransaction.create(
                    tenantId, UUID.randomUUID(), leaseId, RentTransactionType.PAYMENT,
                    BigDecimal.TEN, null, RentTransactionSource.CASH, "admin-1", occurredAt
            );

            assertThatThrownBy(() -> entry.applyTransaction("corr", foreignTx))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_TRANSACTION_MISMATCH);
        }
    }

    @Nested
    class ApplyAdjustment {

        private RentTransaction adjustmentTx(BigDecimal absAmount) {
            return RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.ADJUSTMENT, absAmount,
                    null, RentTransactionSource.ADMIN_ADJUSTMENT, "admin-1", occurredAt
            );
        }

        @Test
        void positiveDeltaIncreasesAmountDue() {
            entry.applyAdjustment("corr", adjustmentTx(new BigDecimal("200.00")), new BigDecimal("200.00"));

            assertThat(entry.getAmountDue()).isEqualByComparingTo("1200.00");
            assertThat(entry.getStatus()).isEqualTo(RentLedgerStatus.DUE);
        }

        @Test
        void negativeDeltaCanPushIntoOverpaidAndFiresEvent() {
            entry.applyTransaction("corr", paymentOf(new BigDecimal("900.00")));
            entry.pullDomainEvents();

            entry.applyAdjustment("corr", adjustmentTx(new BigDecimal("200.00")), new BigDecimal("-200.00"));

            assertThat(entry.getAmountDue()).isEqualByComparingTo("800.00");
            assertThat(entry.getStatus()).isEqualTo(RentLedgerStatus.OVERPAID);
            assertThat(entry.pullDomainEvents()).hasSize(1)
                    .first().isInstanceOf(RentOverpaymentDetected.class);
        }






        @Test
        void rejectsZeroDelta() {
            // Zero delta means RentTransaction.create() is asked to build an
            // ADJUSTMENT transaction with amount = delta.abs() = 0, which
            // RentTransaction's own invariant already forbids — this is caught
            // at the transaction layer before RentLedgerEntry.applyAdjustment's
            // own zero-delta guard is ever reached. Both application-service call
            // sites construct the transaction with delta.abs() before calling
            // applyAdjustment, so this is the actual, correct behavior in
            // production too, not just in this test. See RentLedgerEntry
            // .applyAdjustment's zero-delta branch, which remains reachable only
            // for a null delta paired with a non-zero transaction amount.
            assertThatThrownBy(() -> adjustmentTx(BigDecimal.ZERO))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_TRANSACTION_INVALID_AMOUNT);
        }







        @Test
        void rejectsDeltaThatWouldMakeAmountDueNegative() {
            assertThatThrownBy(() -> entry.applyAdjustment(
                    "corr", adjustmentTx(new BigDecimal("5000.00")), new BigDecimal("-5000.00")
            ))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_INVALID_ADJUSTMENT);
        }

        @Test
        void rejectsNonAdjustmentTransactionType() {
            RentTransaction payment = paymentOf(BigDecimal.TEN);

            assertThatThrownBy(() -> entry.applyAdjustment("corr", payment, new BigDecimal("10.00")))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE);
        }
    }

    @Nested
    class MarkOverdue {

        @Test
        void transitionsFromDueAndFiresEvent() {
            entry.markOverdue("corr", 5);

            assertThat(entry.getStatus()).isEqualTo(RentLedgerStatus.OVERDUE);
            assertThat(entry.pullDomainEvents()).hasSize(1)
                    .first().isInstanceOf(RentOverdueDetected.class);
        }

        @Test
        void isIdempotentWhenAlreadyOverdue() {
            entry.markOverdue("corr", 5);
            entry.pullDomainEvents();

            entry.markOverdue("corr", 9); // re-run — must be a silent no-op, not a re-fire
            assertThat(entry.pullDomainEvents()).isEmpty();
        }

        @Test
        void rejectsCallOnPaidEntry() {
            entry.applyTransaction("corr", paymentOf(new BigDecimal("1000.00")));

            assertThatThrownBy(() -> entry.markOverdue("corr", 3))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_ILLEGAL_TRANSITION);
        }

        @Test
        void rejectsNonPositiveDaysOverdue() {
            assertThatThrownBy(() -> entry.markOverdue("corr", 0))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_INVALID_DAYS_OVERDUE);
        }
    }

    @Nested
    class OverpaymentResolution {

        private RentLedgerEntry overpaidEntry;

        @BeforeEach
        void makeOverpaid() {
            overpaidEntry = RentLedgerEntry.create(
                    tenantId, "corr", leaseId, unitId, tenantProfileId,
                    periodStart, periodEnd, dueDate, new BigDecimal("1000.00"), false
            );
            overpaidEntry.pullDomainEvents();
            RentTransaction overpay = RentTransaction.create(
                    tenantId, overpaidEntry.getId(), leaseId, RentTransactionType.PAYMENT,
                    new BigDecimal("1200.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );
            overpaidEntry.applyTransaction("corr", overpay);
            overpaidEntry.pullDomainEvents();
        }

        @Test
        void refundResolvesExactExcessToPaid() {
            RentTransaction refund = RentTransaction.create(
                    tenantId, overpaidEntry.getId(), leaseId, RentTransactionType.REFUND,
                    new BigDecimal("200.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );

            overpaidEntry.resolveOverpaymentWithRefund(refund);

            assertThat(overpaidEntry.getStatus()).isEqualTo(RentLedgerStatus.PAID);
            assertThat(overpaidEntry.getAmountPaid()).isEqualByComparingTo("1000.00");
        }

        @Test
        void rejectsRefundAmountThatDoesNotExactlyResolveExcess() {
            RentTransaction wrongRefund = RentTransaction.create(
                    tenantId, overpaidEntry.getId(), leaseId, RentTransactionType.REFUND,
                    new BigDecimal("50.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );

            assertThatThrownBy(() -> overpaidEntry.resolveOverpaymentWithRefund(wrongRefund))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_INVALID_REFUND_AMOUNT);
        }

        @Test
        void rejectsResolutionWhenNotOverpaid() {
            RentTransaction refund = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.REFUND,
                    new BigDecimal("10.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );

            assertThatThrownBy(() -> entry.resolveOverpaymentWithRefund(refund))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_ILLEGAL_TRANSITION);
        }

        @Test
        void creditResolutionMovesToPaidWithoutTouchingAmountPaid() {
            BigDecimal amountPaidBefore = overpaidEntry.getAmountPaid();

            overpaidEntry.resolveOverpaymentAsCredit();

            assertThat(overpaidEntry.getStatus()).isEqualTo(RentLedgerStatus.PAID);
            assertThat(overpaidEntry.getAmountPaid()).isEqualByComparingTo(amountPaidBefore);
        }

        @Test
        void creditResolutionRejectsWhenNotOverpaid() {
            assertThatThrownBy(() -> entry.resolveOverpaymentAsCredit())
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_ILLEGAL_TRANSITION);
        }
    }

    @Nested
    class Queries {

        @Test
        void excessAmountIsZeroWhenNotOverpaid() {
            entry.applyTransaction("corr", paymentOf(new BigDecimal("400.00")));
            assertThat(entry.getExcessAmount()).isEqualByComparingTo("0.00");
            assertThat(entry.getBalanceOwed()).isEqualByComparingTo("600.00");
        }

        @Test
        void balanceOwedIsZeroWhenOverpaid() {
            entry.applyTransaction("corr", paymentOf(new BigDecimal("1200.00")));
            assertThat(entry.getBalanceOwed()).isEqualByComparingTo("0.00");
            assertThat(entry.getExcessAmount()).isEqualByComparingTo("200.00");
        }
    }

    @Nested
    class ReverseTransaction {

        @Test
        void reversingAPaymentSubtractsItBackOutAndRecomputesStatus() {
            RentTransaction payment = paymentOf(new BigDecimal("400.00"));
            entry.applyTransaction("corr", payment);
            entry.pullDomainEvents();

            RentTransaction reversal = RentTransaction.reversalOf(payment, tenantId, "admin-1", occurredAt);
            entry.reverseTransaction(payment, reversal);

            assertThat(entry.getAmountPaid()).isEqualByComparingTo("0.00");
            assertThat(entry.getStatus()).isEqualTo(RentLedgerStatus.DUE);
        }

        @Test
        void reversingARefundAddsItBackIn() {
            RentTransaction overpay = paymentOf(new BigDecimal("1200.00"));
            entry.applyTransaction("corr", overpay);
            entry.pullDomainEvents();
            RentTransaction refund = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.REFUND,
                    new BigDecimal("200.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );
            entry.resolveOverpaymentWithRefund(refund);
            assertThat(entry.getAmountPaid()).isEqualByComparingTo("1000.00");

            RentTransaction reversal = RentTransaction.reversalOf(refund, tenantId, "admin-1", occurredAt);
            entry.reverseTransaction(refund, reversal);

            assertThat(entry.getAmountPaid()).isEqualByComparingTo("1200.00");
        }

        @Test
        void amountPaidNeverGoesNegative() {
            // amountPaid=400 after the payment, then a $300 REFUND lands
            // (REFUND is allowed regardless of status) bringing it down to
            // 100 — below the $400 payment being reversed.
            RentTransaction payment = paymentOf(new BigDecimal("400.00"));
            entry.applyTransaction("corr", payment);
            entry.pullDomainEvents();
            RentTransaction refund = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.REFUND,
                    new BigDecimal("300.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );
            entry.applyTransaction("corr", refund);
            entry.pullDomainEvents();
            assertThat(entry.getAmountPaid()).isEqualByComparingTo("100.00");

            RentTransaction reversal = RentTransaction.reversalOf(payment, tenantId, "admin-1", occurredAt);
            entry.reverseTransaction(payment, reversal);

            assertThat(entry.getAmountPaid()).isEqualByComparingTo("0.00");
        }

        @Test
        void rejectsReversingRentCharge() {
            RentTransaction charge = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.RENT_CHARGE,
                    BigDecimal.TEN, null, RentTransactionSource.SYSTEM, "SYSTEM", occurredAt
            );
            RentTransaction reversal = RentTransaction.reversalOf(charge, tenantId, "admin-1", occurredAt);

            assertThatThrownBy(() -> entry.reverseTransaction(charge, reversal))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE);
        }

        @Test
        void rejectsReversingAdjustment() {
            RentTransaction adjustment = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.ADJUSTMENT,
                    new BigDecimal("200.00"), null, RentTransactionSource.ADMIN_ADJUSTMENT, "admin-1", occurredAt
            );
            RentTransaction reversal = RentTransaction.reversalOf(adjustment, tenantId, "admin-1", occurredAt);

            assertThatThrownBy(() -> entry.reverseTransaction(adjustment, reversal))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE);
        }

        @Test
        void rejectsReversingDeposit() {
            // A deposit was never applied to amountPaid in the first place
            // (see rejectsDepositType above), so there is nothing here to
            // reverse — refunds/forfeitures go through the deposit module.
            RentTransaction deposit = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.DEPOSIT,
                    new BigDecimal("200.00"), null, RentTransactionSource.MPESA, "admin-1", occurredAt
            );
            RentTransaction reversal = RentTransaction.reversalOf(deposit, tenantId, "admin-1", occurredAt);

            assertThatThrownBy(() -> entry.reverseTransaction(deposit, reversal))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_UNSUPPORTED_TRANSACTION_TYPE);
        }

        @Test
        void rejectsMismatchedReversal() {
            RentTransaction payment = paymentOf(new BigDecimal("400.00"));
            entry.applyTransaction("corr", payment);
            entry.pullDomainEvents();
            RentTransaction unrelatedPayment = paymentOf(new BigDecimal("50.00"));
            RentTransaction reversalOfSomethingElse =
                    RentTransaction.reversalOf(unrelatedPayment, tenantId, "admin-1", occurredAt);

            assertThatThrownBy(() -> entry.reverseTransaction(payment, reversalOfSomethingElse))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_REVERSAL_MISMATCH);
        }

        @Test
        void rejectsReversalOfWrongType() {
            RentTransaction payment = paymentOf(new BigDecimal("400.00"));
            entry.applyTransaction("corr", payment);
            entry.pullDomainEvents();
            RentTransaction notAReversal = paymentOf(new BigDecimal("10.00"));

            assertThatThrownBy(() -> entry.reverseTransaction(payment, notAReversal))
                    .isInstanceOf(RentLedgerStateException.class)
                    .extracting(ex -> ((RentLedgerStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.RENT_LEDGER_ENTRY_REVERSAL_MISMATCH);
        }
    }

    @Nested
    class ReplayAmountPaid {

        @Test
        void emptyListReplaysToZero() {
            assertThat(RentLedgerEntry.replayAmountPaid(java.util.List.of())).isEqualByComparingTo("0.00");
        }

        @Test
        void sumsPaymentWaiverAndCreditAppliedButIgnoresDeposit() {
            RentTransaction payment = paymentOf(new BigDecimal("400.00"));
            RentTransaction waiver = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.WAIVER,
                    new BigDecimal("100.00"), null, RentTransactionSource.ADMIN_ADJUSTMENT, "admin-1", occurredAt
            );
            RentTransaction credit = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.CREDIT_APPLIED,
                    new BigDecimal("50.00"), null, RentTransactionSource.ADMIN_ADJUSTMENT, "admin-1", occurredAt
            );
            // DEPOSIT is an audit-only receipt row — never counted toward
            // amountPaid, so it must not move the replayed total at all.
            RentTransaction deposit = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.DEPOSIT,
                    new BigDecimal("25.00"), null, RentTransactionSource.MPESA, "admin-1", occurredAt
            );

            BigDecimal replayed = RentLedgerEntry.replayAmountPaid(
                    java.util.List.of(payment, waiver, credit, deposit));

            assertThat(replayed).isEqualByComparingTo("550.00");
        }

        @Test
        void subtractsRefund() {
            RentTransaction payment = paymentOf(new BigDecimal("1200.00"));
            RentTransaction refund = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.REFUND,
                    new BigDecimal("200.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );

            BigDecimal replayed = RentLedgerEntry.replayAmountPaid(java.util.List.of(payment, refund));

            assertThat(replayed).isEqualByComparingTo("1000.00");
        }

        @Test
        void ignoresRentChargeAndAdjustment() {
            RentTransaction charge = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.RENT_CHARGE,
                    new BigDecimal("1000.00"), null, RentTransactionSource.SYSTEM, "SYSTEM", occurredAt
            );
            RentTransaction adjustment = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.ADJUSTMENT,
                    new BigDecimal("200.00"), null, RentTransactionSource.ADMIN_ADJUSTMENT, "admin-1", occurredAt
            );

            BigDecimal replayed = RentLedgerEntry.replayAmountPaid(java.util.List.of(charge, adjustment));

            assertThat(replayed).isEqualByComparingTo("0.00");
        }

        @Test
        void reversalOfPaymentSubtractsBackOut() {
            RentTransaction payment = paymentOf(new BigDecimal("400.00"));
            RentTransaction reversal = RentTransaction.reversalOf(payment, tenantId, "admin-1", occurredAt);

            BigDecimal replayed = RentLedgerEntry.replayAmountPaid(java.util.List.of(payment, reversal));

            assertThat(replayed).isEqualByComparingTo("0.00");
        }

        @Test
        void reversalOfRefundAddsBackIn() {
            RentTransaction payment = paymentOf(new BigDecimal("1200.00"));
            RentTransaction refund = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.REFUND,
                    new BigDecimal("200.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );
            RentTransaction reversalOfRefund = RentTransaction.reversalOf(refund, tenantId, "admin-1", occurredAt);

            BigDecimal replayed = RentLedgerEntry.replayAmountPaid(java.util.List.of(payment, refund, reversalOfRefund));

            assertThat(replayed).isEqualByComparingTo("1200.00");
        }

        @Test
        void canReplayToANegativeTotalRatherThanClampingIt() {
            // A pathological/corrupted log — replayAmountPaid surfaces this
            // rather than hiding it the way the live incremental methods'
            // floor-at-zero clamp would.
            RentTransaction payment = paymentOf(new BigDecimal("100.00"));
            RentTransaction refund = RentTransaction.create(
                    tenantId, entry.getId(), leaseId, RentTransactionType.REFUND,
                    new BigDecimal("300.00"), null, RentTransactionSource.CASH, "admin-1", occurredAt
            );

            BigDecimal replayed = RentLedgerEntry.replayAmountPaid(java.util.List.of(payment, refund));

            assertThat(replayed).isEqualByComparingTo("-200.00");
        }
    }
}