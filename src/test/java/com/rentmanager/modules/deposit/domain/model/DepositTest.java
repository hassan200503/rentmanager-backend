package com.rentmanager.modules.deposit.domain.model;

import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import com.rentmanager.modules.deposit.domain.event.DepositCreatedEvent;
import com.rentmanager.modules.deposit.domain.event.DepositForfeitedEvent;
import com.rentmanager.modules.deposit.domain.event.DepositPaidEvent;
import com.rentmanager.modules.deposit.domain.event.DepositRefundedEvent;
import com.rentmanager.modules.deposit.domain.exception.DepositStateException;
import com.rentmanager.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepositTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID leaseId = UUID.randomUUID();
    private final UUID unitId = UUID.randomUUID();
    private final UUID tenantProfileId = UUID.randomUUID();

    private Deposit deposit;

    @BeforeEach
    void setUp() {
        deposit = Deposit.create(tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("1000.00"), "corr-1", "KES");
        deposit.pullDomainEvents(); // drain DepositCreatedEvent, not under test here
    }

    @Nested
    class Create {

        @Test
        void startsUnpaidAndFiresDepositCreatedEvent() {
            Deposit fresh = Deposit.create(tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("500.00"), "corr-2", "KES");

            assertThat(fresh.getStatus()).isEqualTo(DepositStatus.UNPAID);
            assertThat(fresh.getAmountPaid()).isEqualByComparingTo("0.00");
            assertThat(fresh.getAmountRefunded()).isEqualByComparingTo("0.00");
            assertThat(fresh.getCurrency()).isEqualTo("KES");
            assertThat(fresh.pullDomainEvents()).hasSize(1).first().isInstanceOf(DepositCreatedEvent.class);
        }

        @Test
        void defaultsCurrencyToKesWhenNull() {
            Deposit fresh = Deposit.create(tenantId, leaseId, unitId, tenantProfileId, new BigDecimal("500.00"), "corr-2", null);
            assertThat(fresh.getCurrency()).isEqualTo("KES");
        }

        @Test
        void rejectsNullLeaseId() {
            assertThatThrownBy(() -> Deposit.create(tenantId, null, unitId, tenantProfileId, BigDecimal.TEN, "corr", "KES"))
                    .isInstanceOf(DepositStateException.class)
                    .extracting(ex -> ((DepositStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DEPOSIT_LEASE_NULL);
        }

        @Test
        void rejectsZeroOrNegativeAmountRequired() {
            assertThatThrownBy(() -> Deposit.create(tenantId, leaseId, unitId, tenantProfileId, BigDecimal.ZERO, "corr", "KES"))
                    .isInstanceOf(DepositStateException.class)
                    .extracting(ex -> ((DepositStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DEPOSIT_AMOUNT_REQUIRED);
        }
    }

    @Nested
    class ConfirmPayment {

        @Test
        void movesToHeldAndFiresDepositPaidEvent() {
            deposit.confirmPayment(new BigDecimal("1000.00"), "corr-3");

            assertThat(deposit.getStatus()).isEqualTo(DepositStatus.HELD);
            assertThat(deposit.getAmountPaid()).isEqualByComparingTo("1000.00");
            assertThat(deposit.getPaidAt()).isNotNull();
            assertThat(deposit.pullDomainEvents()).hasSize(1).first().isInstanceOf(DepositPaidEvent.class);
        }

        @Test
        void acceptsOverpayment() {
            deposit.confirmPayment(new BigDecimal("1200.00"), "corr-3");
            assertThat(deposit.getAmountPaid()).isEqualByComparingTo("1200.00");
        }

        @Test
        void rejectsUnderpayment() {
            assertThatThrownBy(() -> deposit.confirmPayment(new BigDecimal("999.00"), "corr-3"))
                    .isInstanceOf(DepositStateException.class)
                    .extracting(ex -> ((DepositStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DEPOSIT_PAYMENT_INSUFFICIENT);
        }

        @Test
        void rejectsWhenNotUnpaid() {
            deposit.confirmPayment(new BigDecimal("1000.00"), "corr-3");

            assertThatThrownBy(() -> deposit.confirmPayment(new BigDecimal("1000.00"), "corr-4"))
                    .isInstanceOf(DepositStateException.class)
                    .extracting(ex -> ((DepositStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DEPOSIT_ALREADY_PAID);
        }
    }

    @Nested
    class Refund {

        @BeforeEach
        void heldDeposit() {
            deposit.confirmPayment(new BigDecimal("1000.00"), "corr-3");
            deposit.pullDomainEvents();
        }

        @Test
        void fullRefundMovesToRefunded() {
            deposit.refund(new BigDecimal("1000.00"), "corr-4");

            assertThat(deposit.getStatus()).isEqualTo(DepositStatus.REFUNDED);
            assertThat(deposit.getAmountRefunded()).isEqualByComparingTo("1000.00");
            assertThat(deposit.getRefundedAt()).isNotNull();
            assertThat(deposit.pullDomainEvents()).hasSize(1).first().isInstanceOf(DepositRefundedEvent.class);
        }

        @Test
        void partialRefundMovesToPartiallyRefunded() {
            deposit.refund(new BigDecimal("600.00"), "corr-4");

            assertThat(deposit.getStatus()).isEqualTo(DepositStatus.PARTIALLY_REFUNDED);
            assertThat(deposit.getAmountRefunded()).isEqualByComparingTo("600.00");
        }

        @Test
        void rejectsRefundExceedingAmountPaid() {
            assertThatThrownBy(() -> deposit.refund(new BigDecimal("1000.01"), "corr-4"))
                    .isInstanceOf(DepositStateException.class)
                    .extracting(ex -> ((DepositStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DEPOSIT_REFUND_EXCEEDS_PAID);
        }

        @Test
        void rejectsRefundWhenNotHeld() {
            deposit.refund(new BigDecimal("1000.00"), "corr-4"); // -> REFUNDED

            assertThatThrownBy(() -> deposit.refund(new BigDecimal("1.00"), "corr-5"))
                    .isInstanceOf(DepositStateException.class)
                    .extracting(ex -> ((DepositStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DEPOSIT_NOT_HELD);
        }
    }

    @Nested
    class Forfeit {

        @BeforeEach
        void heldDeposit() {
            deposit.confirmPayment(new BigDecimal("1000.00"), "corr-3");
            deposit.pullDomainEvents();
        }

        @Test
        void movesToForfeitedAndFiresEvent() {
            deposit.forfeit("corr-4");

            assertThat(deposit.getStatus()).isEqualTo(DepositStatus.FORFEITED);
            assertThat(deposit.pullDomainEvents()).hasSize(1).first().isInstanceOf(DepositForfeitedEvent.class);
        }

        @Test
        void rejectsWhenNotHeld() {
            deposit.forfeit("corr-4"); // -> FORFEITED

            assertThatThrownBy(() -> deposit.forfeit("corr-5"))
                    .isInstanceOf(DepositStateException.class)
                    .extracting(ex -> ((DepositStateException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.DEPOSIT_NOT_HELD);
        }
    }

    @Nested
    class Rehydrate {

        @Test
        void restoresAllFieldsExactly() {
            UUID id = UUID.randomUUID();
            Deposit rehydrated = Deposit.rehydrate(
                    id, tenantId, leaseId, unitId, tenantProfileId,
                    new BigDecimal("1000.00"), new BigDecimal("1000.00"), BigDecimal.ZERO,
                    DepositStatus.HELD, null, null, "USD"
            );

            assertThat(rehydrated.getId()).isEqualTo(id);
            assertThat(rehydrated.getTenantId()).isEqualTo(tenantId);
            assertThat(rehydrated.getStatus()).isEqualTo(DepositStatus.HELD);
            assertThat(rehydrated.getCurrency()).isEqualTo("USD");
        }

        @Test
        void defaultsCurrencyToKesWhenNull() {
            Deposit rehydrated = Deposit.rehydrate(
                    UUID.randomUUID(), tenantId, leaseId, unitId, tenantProfileId,
                    new BigDecimal("1000.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                    DepositStatus.UNPAID, null, null, null
            );
            assertThat(rehydrated.getCurrency()).isEqualTo("KES");
        }
    }
}
