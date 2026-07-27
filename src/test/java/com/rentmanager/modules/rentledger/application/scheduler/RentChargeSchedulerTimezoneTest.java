package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Proves that {@code RentChargeScheduler} always computes billing periods
 * in the {@code Africa/Nairobi} timezone, regardless of the JVM default
 * timezone.
 *
 * The bug this test is designed to catch: if the scheduler had used
 * {@code YearMonth.now()} (no ZoneId) or {@code LocalDate.now()} (no
 * ZoneId) instead of the explicit {@code ZoneId.of("Africa/Nairobi")},
 * a JVM running in UTC-10 could think it's still "last month" at 2 AM
 * Nairobi time (which is UTC+3, so midnight UTC is 3 AM Nairobi, meaning
 * a UTC-10 JVM at 2 AM Nairobi = 11 PM the prior day UTC-10).
 *
 * The test forces the JVM to UTC-10 and verifies the scheduler still
 * computes the correct current billing month (Nairobi time).
 */
class RentChargeSchedulerTimezoneTest {

    private TimeZone originalDefaultTimezone;

    private LeaseRepository leaseRepository;
    private RentLedgerEntryRepository entryRepository;
    private RentLedgerApplicationService ledgerService;

    private RentChargeScheduler scheduler;

    @BeforeEach
    void setUp() {
        originalDefaultTimezone = TimeZone.getDefault();

        leaseRepository = mock(LeaseRepository.class);
        entryRepository = mock(RentLedgerEntryRepository.class);
        ledgerService = mock(RentLedgerApplicationService.class);

        scheduler = new RentChargeScheduler(leaseRepository, entryRepository, ledgerService);
    }

    @AfterEach
    void restoreDefaultTimezone() {
        TimeZone.setDefault(originalDefaultTimezone);
    }

    /**
     * Under UTC-10, midnight Nairobi time (UTC+3) is 1 PM the prior calendar
     * day in UTC-10. This means a scheduler running at 2 AM Nairobi (11 PM UTC,
     * 1 PM UTC-10 the prior day) must still see the correct Nairobi month.
     *
     * We set JVM default to UTC-10, then build a lease whose most recent posted
     * entry is from the previous month (Nairobi), and assert postCharge() is
     * called for the current Nairobi month — not for an incorrect month that a
     * naive {@code YearMonth.now()} (JVM default TZ) would produce.
     */
    @Test
    void postDueChargesForLease_computesCurrentMonthInNairobiTz_notJvmDefault() {
        // Force JVM timezone to far-from-Nairobi zone (UTC-10 = Hawaii time)
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu")); // UTC-10

        // The expected current month according to Africa/Nairobi
        YearMonth expectedNairobiMonth = YearMonth.now(ZoneId.of("Africa/Nairobi"));
        LocalDate expectedPeriodStart = expectedNairobiMonth.atDay(1);

        // Build a lease that started 2 months ago
        Lease lease = buildActiveLease(expectedNairobiMonth.minusMonths(2).atDay(1));

        // The latest posted entry is for last month — scheduler should post this month
        RentLedgerEntry latestEntry = mock(RentLedgerEntry.class);
        when(latestEntry.getBillingPeriodStart())
                .thenReturn(expectedNairobiMonth.minusMonths(1).atDay(1));
        when(entryRepository.findLatestByLeaseId(lease.getId()))
                .thenReturn(Optional.of(latestEntry));

        scheduler.postDueChargesForLease(lease);

        // Verify postCharge was called for the correct Nairobi-time current month
        verify(ledgerService).postCharge(
                eq(lease.getTenantId()),
                anyString(),
                eq(lease.getId()),
                eq(expectedPeriodStart),
                eq(expectedNairobiMonth.atEndOfMonth()),
                eq(expectedPeriodStart) // dueDate = periodStart
        );
    }

    /**
     * Verifies the symmetric case: with a non-Nairobi JVM default, a lease
     * whose latest entry is fully current should produce zero postCharge calls.
     * Prevents false positives from a timezone offset causing the scheduler
     * to think there's a "next" month to post when there isn't.
     */
    @Test
    void postDueChargesForLease_withLatestEntryCurrentMonth_doesNotPostAgain_underAlternativeTz() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles")); // UTC-7/8

        YearMonth currentNairobiMonth = YearMonth.now(ZoneId.of("Africa/Nairobi"));
        Lease lease = buildActiveLease(currentNairobiMonth.minusMonths(1).atDay(1));

        // Latest entry is already for the current Nairobi month
        RentLedgerEntry latestEntry = mock(RentLedgerEntry.class);
        when(latestEntry.getBillingPeriodStart()).thenReturn(currentNairobiMonth.atDay(1));
        when(entryRepository.findLatestByLeaseId(lease.getId()))
                .thenReturn(Optional.of(latestEntry));

        scheduler.postDueChargesForLease(lease);

        // Nothing to post — current month already covered
        verifyNoInteractions(ledgerService);
    }

    /**
     * Confirms the catch-up behavior: if the scheduler missed 2 months
     * (regardless of JVM timezone), it posts both missing periods.
     */
    @Test
    void postDueChargesForLease_catchesUpMissedMonths_underAlternativeTz() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York")); // UTC-4/5

        YearMonth currentNairobiMonth = YearMonth.now(ZoneId.of("Africa/Nairobi"));
        Lease lease = buildActiveLease(currentNairobiMonth.minusMonths(3).atDay(1));

        // Latest entry is 2 months old — scheduler should post 2 months
        RentLedgerEntry latestEntry = mock(RentLedgerEntry.class);
        when(latestEntry.getBillingPeriodStart())
                .thenReturn(currentNairobiMonth.minusMonths(2).atDay(1));
        when(entryRepository.findLatestByLeaseId(lease.getId()))
                .thenReturn(Optional.of(latestEntry));

        scheduler.postDueChargesForLease(lease);

        // Should have posted the last 2 months (month-1 and month-0)
        verify(ledgerService, times(2)).postCharge(
                eq(lease.getTenantId()),
                anyString(),
                eq(lease.getId()),
                any(LocalDate.class),
                any(LocalDate.class),
                any(LocalDate.class)
        );
    }

    private Lease buildActiveLease(LocalDate startDate) {
        UUID tenantId = UUID.randomUUID();
        Lease lease = Lease.create(
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "LSE-TZ-TEST-" + System.nanoTime(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                startDate,
                startDate.plusYears(1),
                new BigDecimal("20000"),
                new BigDecimal("30000"),
                BigDecimal.ZERO,
                0,
                false
        );
        lease.approve();
        lease.markAwaitingDeposit();
        lease.activate();
        return lease;
    }
}
