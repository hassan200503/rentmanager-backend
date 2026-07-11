package com.rentmanager.modules.rentledger.api.dto.response;

import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RentLedgerEntryResponse(
        UUID id,
        UUID leaseId,
        UUID unitId,
        UUID tenantProfileId,
        LocalDate billingPeriodStart,
        LocalDate billingPeriodEnd,
        LocalDate dueDate,
        BigDecimal amountDue,
        BigDecimal amountPaid,
        BigDecimal balanceOwed,
        BigDecimal excessAmount,
        String status,
        boolean prorated,
        Long version
) {
    public static RentLedgerEntryResponse from(RentLedgerEntry entry) {
        return new RentLedgerEntryResponse(
                entry.getId(),
                entry.getLeaseId(),
                entry.getUnitId(),
                entry.getTenantProfileId(),
                entry.getBillingPeriodStart(),
                entry.getBillingPeriodEnd(),
                entry.getDueDate(),
                entry.getAmountDue(),
                entry.getAmountPaid(),
                entry.getBalanceOwed(),
                entry.getExcessAmount(),
                entry.getStatus().name(),
                entry.isProrated(),
                entry.getVersion()
        );
    }
}