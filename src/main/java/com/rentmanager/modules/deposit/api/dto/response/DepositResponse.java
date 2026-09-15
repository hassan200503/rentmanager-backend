package com.rentmanager.modules.deposit.api.dto.response;

import com.rentmanager.modules.deposit.domain.model.Deposit;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DepositResponse(
        UUID id,
        UUID leaseId,
        UUID unitId,
        UUID tenantProfileId,
        BigDecimal amountRequired,
        BigDecimal amountPaid,
        BigDecimal amountRefunded,
        String currency,
        String status,
        LocalDateTime paidAt,
        LocalDateTime refundedAt,
        BigDecimal deductionAmount,
        String deductionReason,
        String refundReference,
        String refundRemarks,
        // STK push pending refund state — null when no refund is in progress.
        boolean hasPendingRefund,
        String pendingRefundPhone,
        LocalDateTime pendingRefundInitiatedAt,
        // Renter info enriched by the controller for the refund form display.
        String renterName,
        String renterPhone
) {
    public static DepositResponse from(Deposit deposit) {
        return new DepositResponse(
                deposit.getId(),
                deposit.getLeaseId(),
                deposit.getUnitId(),
                deposit.getTenantProfileId(),
                deposit.getAmountRequired(),
                deposit.getAmountPaid(),
                deposit.getAmountRefunded(),
                deposit.getCurrency(),
                deposit.getStatus().name(),
                deposit.getPaidAt(),
                deposit.getRefundedAt(),
                deposit.getDeductionAmount(),
                deposit.getDeductionReason(),
                deposit.getRefundReference(),
                deposit.getRefundRemarks(),
                deposit.hasPendingRefund(),
                deposit.getPendingRefundPhone(),
                deposit.getPendingRefundInitiatedAt(),
                null,
                null
        );
    }

    public static DepositResponse fromWithRenterInfo(Deposit deposit, String renterName, String renterPhone) {
        return new DepositResponse(
                deposit.getId(),
                deposit.getLeaseId(),
                deposit.getUnitId(),
                deposit.getTenantProfileId(),
                deposit.getAmountRequired(),
                deposit.getAmountPaid(),
                deposit.getAmountRefunded(),
                deposit.getCurrency(),
                deposit.getStatus().name(),
                deposit.getPaidAt(),
                deposit.getRefundedAt(),
                deposit.getDeductionAmount(),
                deposit.getDeductionReason(),
                deposit.getRefundReference(),
                deposit.getRefundRemarks(),
                deposit.hasPendingRefund(),
                deposit.getPendingRefundPhone(),
                deposit.getPendingRefundInitiatedAt(),
                renterName,
                renterPhone
        );
    }
}
