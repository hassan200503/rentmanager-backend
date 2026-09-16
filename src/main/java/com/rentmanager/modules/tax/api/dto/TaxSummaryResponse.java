package com.rentmanager.modules.tax.api.dto;

import com.rentmanager.modules.tax.domain.enums.MonthlyFilingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxSummaryResponse(
        long attentionRequiredCount,
        BigDecimal currentMriRate,
        LocalDate lastFilingPeriod,
        BigDecimal lastFilingTaxDue,
        MonthlyFilingStatus lastFilingStatus,
        LocalDate nextFilingDeadline
) {
}
