package com.rentmanager.modules.tax.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload handed to the eRITS transmission port (monthly filing
 * submission).
 */
public record ErisMonthlyFilingSubmission(
        UUID filingId,
        UUID tenantId,
        String landlordKraPin,
        LocalDate period,
        BigDecimal grossRentalIncome,
        BigDecimal mriRateApplied,
        BigDecimal mriTaxDue,
        boolean nilReturn
) {
}
