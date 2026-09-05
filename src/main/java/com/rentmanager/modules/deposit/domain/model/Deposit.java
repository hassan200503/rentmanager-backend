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

    public void refund(BigDecimal refundAmount, String correlationId) {
        if (status != DepositStatus.HELD) {
            throw new DepositStateException("Only held deposits can be refunded", ErrorCode.DEPOSIT_NOT_HELD);
        }
        if (refundAmount == null || refundAmount.compareTo(amountPaid) > 0) {
            throw new DepositStateException(
                    "Refund amount cannot exceed amount paid",
                    ErrorCode.DEPOSIT_REFUND_EXCEEDS_PAID
            );
        }

        this.amountRefunded = refundAmount;
        this.status = refundAmount.compareTo(amountPaid) == 0
                ? DepositStatus.REFUNDED
                : DepositStatus.PARTIALLY_REFUNDED;
        this.refundedAt = LocalDateTime.now();

        registerEvent(new DepositRefundedEvent(
                getTenantId(), getId(), correlationId, leaseId, refundAmount
        ));
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
            String currency
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
                .build();

        deposit.setId(id);
        deposit.assignTenant(tenantId);
        return deposit;
    }
}