package com.rentmanager.modules.rentledger.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TenantDashboardResponse(
        UUID tenantId,
        String tenantName,
        String tenantPhone,
        String tenantEmail,
        BigDecimal currentBalance,
        UUID currentEntryId,
        LocalDate nextDueDate,
        BigDecimal nextDueAmount,
        BigDecimal overdueAmount,
        String leaseStatus,
        String unitNumber,
        String propertyName,
        BigDecimal monthlyRent,
        BigDecimal depositAmount,
        List<PaymentHistoryItem> recentPayments
) {
    public record PaymentHistoryItem(
            UUID id,
            String type,
            BigDecimal amount,
            String source,
            String externalReference,
            String occurredAt,
            String status,
            String billingPeriodStart,
            String billingPeriodEnd,
            String mpesaTransactionId
    ) {}
}
