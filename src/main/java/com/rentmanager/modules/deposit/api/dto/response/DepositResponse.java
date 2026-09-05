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
        LocalDateTime refundedAt
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
                deposit.getRefundedAt()
        );
    }
}
