package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.application.dto.RentLedgerSummary;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentLedgerEntryJpaRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Computes the landlord dashboard's financial figures.
 *
 * <h2>Four queries, all aggregates</h2>
 * The dashboard previously built its numbers from five unpaginated list
 * queries and then invented the financial ones outright. Everything here is
 * summed in PostgreSQL and returns a single scalar, so a landlord with three
 * units and a landlord with three hundred both cost the same to render.
 *
 * <h2>Month boundaries are Nairobi's, not the server's</h2>
 * "Collected this month" has to mean the month the landlord is living in. A
 * container running UTC would otherwise roll the month over three hours late
 * and briefly report the wrong figure on the 1st — small, invisible, and
 * exactly the kind of thing that erodes confidence in every other number on
 * the page.
 */
@Service
@RequiredArgsConstructor
public class RentLedgerSummaryService {

    private static final ZoneId TZ = ZoneId.of("Africa/Nairobi");

    /** Statuses that can still owe money. PAID is settled; OVERPAID owes nothing. */
    private static final List<RentLedgerStatus> UNSETTLED = List.of(
            RentLedgerStatus.DUE,
            RentLedgerStatus.PARTIALLY_PAID,
            RentLedgerStatus.OVERDUE
    );

    private static final String DEFAULT_CURRENCY = "KES";

    private final RentLedgerEntryJpaRepository ledgerRepository;
    private final TenantRepository tenantRepository;
    private final ObjectProvider<Clock> clockProvider;

    @Transactional(readOnly = true)
    public RentLedgerSummary getSummary(UUID tenantId) {
        Clock clock = clockProvider.getIfAvailable(() -> Clock.system(TZ));
        LocalDate today = LocalDate.now(clock.withZone(TZ));

        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);

        BigDecimal collected =
                orZero(ledgerRepository.sumCollectedBetween(tenantId, monthStart, nextMonthStart));
        BigDecimal outstanding =
                orZero(ledgerRepository.sumOutstanding(tenantId, UNSETTLED));
        BigDecimal overdue =
                orZero(ledgerRepository.sumByStatus(tenantId, RentLedgerStatus.OVERDUE));
        long overdueCount =
                ledgerRepository.countByStatusUnsettled(tenantId, RentLedgerStatus.OVERDUE);

        return new RentLedgerSummary(
                collected, outstanding, overdue, overdueCount, currencyFor(tenantId));
    }

    /**
     * The landlord's configured currency, falling back to KES.
     *
     * <p>Read from the organisation rather than from the entries, because a
     * landlord with no entries yet still has a currency, and a summary of
     * zeroes still has to be labelled with something.
     */
    private String currencyFor(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(t -> t.getCurrency() == null || t.getCurrency().isBlank()
                        ? DEFAULT_CURRENCY
                        : t.getCurrency())
                .orElse(DEFAULT_CURRENCY);
    }

    /**
     * COALESCE covers SUM-over-no-rows, but a repository method can still hand
     * back null if a query is ever changed without it. Money must never be
     * null on the way to a UI that will render it.
     */
    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
