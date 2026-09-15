package com.rentmanager.modules.deposit.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import com.rentmanager.modules.deposit.domain.event.*;
import com.rentmanager.modules.deposit.domain.exception.DepositStateException;
import com.rentmanager.shared.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Deposit extends AggregateRoot {

    private UUID leaseId;
    private UUID unitId;
    private UUID tenantProfileId;
    private BigDecimal amountRequired;
    private BigDecimal amountPaid;
    private BigDecimal amountRefunded;
    private DepositStatus status;
    private LocalDateTime paidAt;
    private LocalDateTime refundedAt;
    private String currency;

    // Refund detail — populated only after refund() is called.
    // deductionAmount: portion kept by landlord (0 for full refund).
    // deductionReason: free-text, required when deductionAmount > 0.
    // refundReference: Safaricom M-Pesa confirmation code, required when money was sent.
    // refundRemarks: optional landlord notes.
    private BigDecimal deductionAmount;
    private String deductionReason;
    private String refundReference;
    private String refundRemarks;

    // In-flight STK push authorisation — set by markRefundPending(), cleared by
    // completePendingRefund() or clearPendingRefund(). The checkout request ID
    // is indexed so the callback handler can locate this deposit instantly.
    private String pendingRefundCheckoutRequestId;
    private String pendingRefundPhone;
    private BigDecimal pendingRefundDeduction;
    private String pendingRefundDeductionReason;
    private String pendingRefundRemarks;
    private LocalDateTime pendingRefundInitiatedAt;

    public static Deposit create(
            UUID tenantId,
            UUID leaseId,
            UUID unitId,
            UUID tenantProfileId,
            BigDecimal amountRequired,
            String correlationId,
            String currency
    ) {
        if (leaseId == null) {
            throw new DepositStateException("leaseId cannot be null", ErrorCode.DEPOSIT_LEASE_NULL);
        }
        if (unitId == null) {
            throw new DepositStateException("unitId cannot be null", ErrorCode.DEPOSIT_UNIT_NULL);
        }
        if (tenantProfileId == null) {
            throw new DepositStateException("tenantProfileId cannot be null", ErrorCode.DEPOSIT_TENANT_PROFILE_NULL);
        }
        if (amountRequired == null || amountRequired.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DepositStateException("amountRequired must be > 0", ErrorCode.DEPOSIT_AMOUNT_REQUIRED);
        }

        Deposit deposit = Deposit.builder()
                .leaseId(leaseId)
                .unitId(unitId)
                .tenantProfileId(tenantProfileId)
                .amountRequired(amountRequired)
                .amountPaid(BigDecimal.ZERO)
                .amountRefunded(BigDecimal.ZERO)
                .status(DepositStatus.UNPAID)
                .currency(currency != null ? currency : "KES")
                .build();

        deposit.setId(UUID.randomUUID());
        deposit.assignTenant(tenantId);

        deposit.registerEvent(new DepositCreatedEvent(
                tenantId, deposit.getId(), "SYSTEM", leaseId
        ));

        return deposit;
    }

    public void confirmPayment(BigDecimal amountPaid, String correlationId) {
        if (status != DepositStatus.UNPAID) {
            throw new DepositStateException("Only unpaid deposits can be confirmed", ErrorCode.DEPOSIT_ALREADY_PAID);
        }
        if (amountPaid == null || amountPaid.compareTo(amountRequired) < 0) {
            throw new DepositStateException(
                    "Deposit payment is less than required amount",
                    ErrorCode.DEPOSIT_PAYMENT_INSUFFICIENT
            );
        }

        this.amountPaid = amountPaid;
        this.status = DepositStatus.HELD;
        this.paidAt = LocalDateTime.now();

        registerEvent(new DepositPaidEvent(
                getTenantId(), getId(), correlationId, leaseId, unitId, tenantProfileId
        ));
    }

    /**
     * Records a deposit refund with optional landlord deduction.
     *
     * @param deductionAmount portion kept by the landlord (0 for full refund,
     *                        must be < amountPaid — use forfeit() if deducting
     *                        the full amount)
     * @param deductionReason explanation for any deduction; validated as
     *                        non-blank by the service when deductionAmount > 0
     * @param refundReference Safaricom M-Pesa confirmation code; validated as
     *                        non-blank by the service when money is sent
     * @param refundRemarks   optional landlord notes
     * @param correlationId   tracing ID for event publishing
     */
    public void refund(BigDecimal deductionAmount, String deductionReason,
                       String refundReference, String refundRemarks, String correlationId) {
        if (status != DepositStatus.HELD) {
            throw new DepositStateException("Only held deposits can be refunded", ErrorCode.DEPOSIT_NOT_HELD);
        }
        BigDecimal effectiveDeduction = deductionAmount != null ? deductionAmount : BigDecimal.ZERO;
        if (effectiveDeduction.compareTo(BigDecimal.ZERO) < 0) {
            throw new DepositStateException("Deduction amount cannot be negative", ErrorCode.DEPOSIT_REFUND_EXCEEDS_PAID);
        }
        if (effectiveDeduction.compareTo(amountPaid) >= 0) {
            throw new DepositStateException(
                    "Deduction equals or exceeds amount paid — use forfeit() for full deduction",
                    ErrorCode.DEPOSIT_REFUND_EXCEEDS_PAID
            );
        }

        BigDecimal refundAmount = amountPaid.subtract(effectiveDeduction);

        this.deductionAmount = effectiveDeduction;
        this.deductionReason = deductionReason;
        this.refundReference = refundReference;
        this.refundRemarks = refundRemarks;
        this.amountRefunded = refundAmount;
        this.status = effectiveDeduction.compareTo(BigDecimal.ZERO) > 0
                ? DepositStatus.PARTIALLY_REFUNDED
                : DepositStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();

        registerEvent(new DepositRefundedEvent(
                getTenantId(), getId(), correlationId, leaseId, refundAmount
        ));
    }

    public void markRefundPending(String checkoutRequestId, String recipientPhone,
                                   BigDecimal deduction, String deductionReason, String remarks) {
        if (status != DepositStatus.HELD) {
            throw new DepositStateException("Only held deposits can initiate a refund", ErrorCode.DEPOSIT_NOT_HELD);
        }
        if (pendingRefundCheckoutRequestId != null) {
            throw new DepositStateException(
                    "A refund is already in progress — wait for M-Pesa confirmation or cancel first",
                    ErrorCode.DEPOSIT_NOT_HELD);
        }
        this.pendingRefundCheckoutRequestId = checkoutRequestId;
        this.pendingRefundPhone = recipientPhone;
        this.pendingRefundDeduction = deduction != null ? deduction : BigDecimal.ZERO;
        this.pendingRefundDeductionReason = deductionReason;
        this.pendingRefundRemarks = remarks;
        this.pendingRefundInitiatedAt = LocalDateTime.now();
    }

    public void completePendingRefund(String mpesaReceipt, String correlationId) {
        if (pendingRefundCheckoutRequestId == null) {
            throw new DepositStateException("No pending refund to complete", ErrorCode.DEPOSIT_NOT_HELD);
        }
        BigDecimal deduction = pendingRefundDeduction != null ? pendingRefundDeduction : BigDecimal.ZERO;
        // refund() validates status is HELD and deduction < amountPaid internally
        refund(deduction, pendingRefundDeductionReason, mpesaReceipt, pendingRefundRemarks, correlationId);
        clearPendingRefund();
    }

    public void clearPendingRefund() {
        this.pendingRefundCheckoutRequestId = null;
        this.pendingRefundPhone = null;
        this.pendingRefundDeduction = null;
        this.pendingRefundDeductionReason = null;
        this.pendingRefundRemarks = null;
        this.pendingRefundInitiatedAt = null;
    }

    public boolean hasPendingRefund() {
        return pendingRefundCheckoutRequestId != null;
    }

    public void forfeit(String correlationId) {
        if (status != DepositStatus.HELD) {
            throw new DepositStateException("Only held deposits can be forfeited", ErrorCode.DEPOSIT_NOT_HELD);
        }
        this.status = DepositStatus.FORFEITED;

        registerEvent(new DepositForfeitedEvent(
                getTenantId(), getId(), correlationId, leaseId
        ));
    }

    public static Deposit rehydrate(
            UUID id,
            UUID tenantId,
            UUID leaseId,
            UUID unitId,
            UUID tenantProfileId,
            BigDecimal amountRequired,
            BigDecimal amountPaid,
            BigDecimal amountRefunded,
            DepositStatus status,
            LocalDateTime paidAt,
            LocalDateTime refundedAt,
            String currency,
            BigDecimal deductionAmount,
            String deductionReason,
            String refundReference,
            String refundRemarks,
            String pendingRefundCheckoutRequestId,
            String pendingRefundPhone,
            BigDecimal pendingRefundDeduction,
            String pendingRefundDeductionReason,
            String pendingRefundRemarks,
            LocalDateTime pendingRefundInitiatedAt
    ) {
        Deposit deposit = Deposit.builder()
                .leaseId(leaseId)
                .unitId(unitId)
                .tenantProfileId(tenantProfileId)
                .amountRequired(amountRequired)
                .amountPaid(amountPaid)
                .amountRefunded(amountRefunded)
                .status(status)
                .paidAt(paidAt)
                .refundedAt(refundedAt)
                .currency(currency != null ? currency : "KES")
                .deductionAmount(deductionAmount != null ? deductionAmount : BigDecimal.ZERO)
                .deductionReason(deductionReason)
                .refundReference(refundReference)
                .refundRemarks(refundRemarks)
                .pendingRefundCheckoutRequestId(pendingRefundCheckoutRequestId)
                .pendingRefundPhone(pendingRefundPhone)
                .pendingRefundDeduction(pendingRefundDeduction)
                .pendingRefundDeductionReason(pendingRefundDeductionReason)
                .pendingRefundRemarks(pendingRefundRemarks)
                .pendingRefundInitiatedAt(pendingRefundInitiatedAt)
                .build();

        deposit.setId(id);
        deposit.assignTenant(tenantId);
        return deposit;
    }
}