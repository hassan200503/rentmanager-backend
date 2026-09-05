package com.rentmanager.modules.tax.infrastructure.scheduler;

import com.rentmanager.modules.tax.application.port.ResidentialRentPaymentAggregationPort;
import com.rentmanager.modules.tax.application.service.MonthlyRentalFilingComputationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Computes each landlord's Monthly Rental Income filing for the previous
 * month, at 04:00 on the first day of each month (a late-payment grace
 * window before the 5th-of-month eRITS remittance expectation).
 *
 * <p>Drives the computation from landlords that actually recorded
 * residential payments in the period; NIL filings are still produced for
 * them when their only row happened in a different month — the eRITS nil
 * return replaces a zero-value filing (see the computation service).
 *
 * <p>Not {@code @Transactional} itself; per-tenant computation runs in the
 * service's own transaction so one bad tenant cannot block the batch.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyRentalFilingScheduler {

    private final ResidentialRentPaymentAggregationPort residentialPayments;
    private final MonthlyRentalFilingComputationService computationService;

    @Scheduled(cron = "0 0 4 1 * *", zone = "Africa/Nairobi") // 04:00 on the 1st of every month
    public void computePreviousMonthFilings() {
        // The cron is pinned to Nairobi and this must agree with it: at 04:00
        // Nairobi it is still the previous day in UTC, so an unzoned now()
        // would compute the wrong previous month.
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        LocalDate previousMonthStart = today.minusMonths(1).withDayOfMonth(1);

        List<UUID> tenantIds = residentialPayments
                .findTenantIdsWithResidentialPaymentsInPeriod(
                        previousMonthStart,
                        previousMonthStart.plusMonths(1));

        if (tenantIds.isEmpty()) {
            log.info("Monthly filing sweep: no landlords with residential payments in {}", previousMonthStart);
            return;
        }

        log.info("Monthly filing sweep: computing filings for {} landlord(s) for {}", tenantIds.size(), previousMonthStart);

        for (UUID tenantId : tenantIds) {
            try {
                computationService.computeForPeriod(tenantId, previousMonthStart);
            } catch (Exception e) {
                log.error("Failed to compute monthly filing for tenant {} period {} — will retry next month",
                        tenantId, previousMonthStart, e);
            }
        }
    }
}