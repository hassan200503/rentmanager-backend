package com.rentmanager.modules.rentledger.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TenantPaymentSummaryResponse(
        BigDecimal totalPaid,
        BigDecimal totalDue,
        BigDecimal currentBalance,
        BigDecimal overdueAmount,
        int paymentsThisYear,
        LocalDateTime lastPaymentDate,
        BigDecimal lastPaymentAmount
) {}
