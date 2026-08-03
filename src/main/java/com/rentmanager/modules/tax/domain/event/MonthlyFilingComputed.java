package com.rentmanager.modules.tax.domain.event;

import com.rentmanager.domain.base.DomainEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Fired when a landlord's Monthly Rental Income filing has been computed
 * and persisted. Consumed by future phases (landlord preview/approval
 * notifications, eRITS transmission).
 */
public class MonthlyFilingComputed extends DomainEvent {

    private final LocalDate period;
    private final BigDecimal grossRentalIncome;
    private final BigDecimal mriTaxDue;

    public MonthlyFilingComputed(
            UUID tenantId,
            UUID filingId,
            String correlationId,
            LocalDate period,
            BigDecimal grossRentalIncome,
            BigDecimal mriTaxDue
    ) {
        super(tenantId, filingId, correlationId);
        this.period = period;
        this.grossRentalIncome = grossRentalIncome;
        this.mriTaxDue = mriTaxDue;
    }

    public LocalDate getPeriod() { return period; }
    public BigDecimal getGrossRentalIncome() { return grossRentalIncome; }
    public BigDecimal getMriTaxDue() { return mriTaxDue; }

    @Override
    public String eventType() {
        return "MonthlyFilingComputed";
    }
}
