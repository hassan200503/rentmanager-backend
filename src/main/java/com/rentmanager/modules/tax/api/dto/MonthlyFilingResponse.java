package com.rentmanager.modules.tax.api.dto;

import com.rentmanager.modules.tax.domain.enums.MonthlyFilingStatus;
import com.rentmanager.modules.tax.domain.model.MonthlyRentalIncomeFiling;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MonthlyFilingResponse(
        UUID id,
        LocalDate period,
        BigDecimal grossRentalIncome,
        boolean nilReturn,
        BigDecimal mriRateApplied,
        BigDecimal mriTaxDue,
        MonthlyFilingStatus status,
        LocalDateTime computedAt,
        LocalDateTime filedAt,
        LocalDateTime transmittedAt
) {

    public static MonthlyFilingResponse from(MonthlyRentalIncomeFiling filing) {
        return new MonthlyFilingResponse(
                filing.getId(),
                filing.getPeriod(),
                filing.getGrossRentalIncome(),
                filing.isNilReturn(),
                filing.getMriRateApplied(),
                filing.getMriTaxDue(),
                filing.getStatus(),
                filing.getComputedAt(),
                filing.getFiledAt(),
                filing.getTransmittedAt()
        );
    }
}
