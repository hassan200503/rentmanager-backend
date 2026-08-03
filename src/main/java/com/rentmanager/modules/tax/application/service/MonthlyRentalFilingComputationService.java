package com.rentmanager.modules.tax.application.service;

import com.rentmanager.modules.tax.application.config.TaxProperties;
import com.rentmanager.modules.tax.application.port.ResidentialRentPaymentAggregationPort;
import com.rentmanager.modules.tax.domain.event.MonthlyFilingComputed;
import com.rentmanager.modules.tax.domain.model.MonthlyRentalIncomeFiling;
import com.rentmanager.modules.tax.domain.model.MriRateSchedule;
import com.rentmanager.modules.tax.domain.repository.MonthlyRentalIncomeFilingRepository;
import com.rentmanager.shared.events.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Computes a landlord's Monthly Rental Income filing for a given month.
 *
 * <p>gross_rental_income = sum of RESIDENTIAL rent payments (type =
 * PAYMENT) recorded in the month (COMMERCIAL rent never enters the MRI
 * regime — the aggregation port filters it in the query). mri_tax_due uses
 * the ACTIVE rate for the month from {@link MriRatePolicyService} — a
 * pre-staged SCHEDULED (unverified) rate is never applied. A landlord with
 * no payments gets a NIL filing rather than none at all.
 *
 * <p>Idempotent: a (tenant, period) filing is created at most once. If the
 * filing already exists it is returned untouched — recomputation after
 * late-arriving payments is a later-phase refinement, and the unique key
 * prevents duplicates regardless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyRentalFilingComputationService {

    private final MonthlyRentalIncomeFilingRepository filingRepository;
    private final ResidentialRentPaymentAggregationPort residentialPayments;
    private final MriRatePolicyService mriRatePolicy;
    private final DomainEventPublisher eventPublisher;
    private final TaxProperties taxProperties;

    /**
     * Computes (or returns the existing, idempotent) filing for the month.
     * {@code period} may be any day of the month; it is normalised to the
     * first day. Returns empty when filings are disabled via
     * {@code app.tax.filing-enabled=false}.
     */
    @Transactional
    public Optional<MonthlyRentalIncomeFiling> computeForPeriod(UUID landlordTenantId, LocalDate period) {
        if (!taxProperties.isFilingEnabled()) {
            return Optional.empty();
        }
        if (landlordTenantId == null || period == null) {
            throw new IllegalArgumentException("tenantId and period are required");
        }

        LocalDate monthStart = period.withDayOfMonth(1);
        LocalDate monthEndExclusive = monthStart.plusMonths(1);

        Optional<MonthlyRentalIncomeFiling> existing =
                filingRepository.findByTenantIdAndPeriod(landlordTenantId, monthStart);
        if (existing.isPresent()) {
            return existing;
        }

        MriRateSchedule rate = mriRatePolicy.activeRateAsOf(monthStart);

        BigDecimal gross = residentialPayments.sumResidentialPaymentsInPeriod(
                landlordTenantId, monthStart, monthEndExclusive);

        MonthlyRentalIncomeFiling filing = MonthlyRentalIncomeFiling.compute(
                landlordTenantId,
                monthStart,
                gross,
                rate.getRatePercent()
        );

        MonthlyRentalIncomeFiling saved = filingRepository.save(filing);

        eventPublisher.publish(new MonthlyFilingComputed(
                saved.getTenantId(),
                saved.getId(),
                "MRI-" + landlordTenantId + "-" + monthStart,
                saved.getPeriod(),
                saved.getGrossRentalIncome(),
                saved.getMriTaxDue()
        ));

        log.info("Computed monthly filing {} for tenant {} ({} gross, {} tax due) at rate {}",
                saved.getId(), landlordTenantId, saved.getGrossRentalIncome(),
                saved.getMriTaxDue(), saved.getMriRateApplied());

        return Optional.of(saved);
    }
}