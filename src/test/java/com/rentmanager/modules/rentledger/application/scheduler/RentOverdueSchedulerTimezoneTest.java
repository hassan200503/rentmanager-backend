package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Proves that {@code RentOverdueScheduler} evaluates overdue status against
 * {@code Africa/Nairobi} local date, not the JVM default timezone.
 *
 * The bug this test is designed to catch: if the scheduler had called
 * {@code LocalDate.now()} without a ZoneId, a JVM in UTC-10 running at
 * 2:30 AM Nairobi (11:30 PM prior day in UTC-10) would think today's date
 * is one day earlier than Nairobi's date — potentially skipping entries
 * that should be flagged as overdue.
 *
 * Each test forces a non-Nairobi JVM default, then builds an entry whose
 * dueDate is just past the Nairobi local date but not yet past the UTC-10
 * local date, and verifies the scheduler correctly flags it as overdue
 * (because it uses Nairobi time, not JVM default time).
 */
class RentOverdueSchedulerTimezoneTest {

    private TimeZone originalDefaultTimezone;

    private RentLedgerEntryRepository entryRepository;
    private LeaseRepository leaseRepository;
    private RentLedgerApplicationService ledgerService;

    private RentOverdueScheduler scheduler;

    @BeforeEach
    void setUp() {
        originalDefaultTimezone = TimeZone.getDefault();

        entryRepository = mock(RentLedgerEntryRepository.class);
        leaseRepository = mock(LeaseRepository.class);
        ledgerService = mock(RentLedgerApplicationService.class);

        scheduler = new RentOverdueScheduler(entryRepository, leaseRepository, ledgerService);
    }

    @AfterEach
    void restoreDefaultTimezone() {
        TimeZone.setDefault(originalDefaultTimezone);
    }

    /**
     * With JVM defaulted to UTC-10, an entry due yesterday (Nairobi time)
     * must still be flagged as overdue — not skipped because "yesterday" in
     * UTC-10 is further back than "yesterday" in Africa/Nairobi.
     *
     * A lease with 0 grace period days means the entry is overdue the moment
     * it passes its dueDate in Nairobi time.
     */
    @Test
    void markOverdueIfPastGrace_flagsOverdue_whenPastDueDateInNairobiTz_underAlternativeJvmTz() {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu")); // UTC-10

        // Yesterday's date in Africa/Nairobi — should be overdue with 0 grace days
        LocalDate nairobiYesterday = LocalDate.now(ZoneId.of("Africa/Nairobi")).minusDays(1);

        UUID tenantId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        RentLedgerEntry entry = buildDueEntry(tenantId, leaseId, nairobiYesterday);
        Lease lease = buildLeaseWithGrace(leaseId, tenantId, 0);

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(lease));

        UUID entryId = entry.getId(); // capture before passing to verify to avoid Mockito matcher misuse
        scheduler.markOverdueIfPastGrace(entry);

        // Must call markOverdue — entry is past due in Nairobi time
        verify(ledgerService).markOverdue(
                eq(tenantId),
                anyString(),
                eq(entryId),
                eq(1) // 1 day overdue
        );
    }

    /**
     * An entry due today (Nairobi time, 0 grace days) must NOT be flagged yet —
     * "today is the due date" is not overdue under this lease's terms.
     * Verifies the scheduler uses Nairobi's "today" boundary, not a JVM default.
     */
    @Test
    void markOverdueIfPastGrace_doesNotFlag_whenDueDateIsToday_nairobiTz() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles")); // UTC-7/8

        LocalDate nairobiToday = LocalDate.now(ZoneId.of("Africa/Nairobi"));

        UUID tenantId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        RentLedgerEntry entry = buildDueEntry(tenantId, leaseId, nairobiToday);
        Lease lease = buildLeaseWithGrace(leaseId, tenantId, 0);

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(lease));

        scheduler.markOverdueIfPastGrace(entry);

        // Due date is today — not yet past — must NOT mark overdue
        verifyNoInteractions(ledgerService);
    }

    /**
     * Grace period is respected: an entry 3 days past its dueDate with a 5-day
     * grace period must NOT be flagged yet. This is independent of timezone but
     * confirms the grace-period logic still works correctly under a non-Nairobi
     * JVM default.
     */
    @Test
    void markOverdueIfPastGrace_respectsGracePeriod_underAlternativeJvmTz() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York")); // UTC-4/5

        LocalDate nairobiToday = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        // Due 3 days ago, 5-day grace period — still within grace
        LocalDate dueDate = nairobiToday.minusDays(3);

        UUID tenantId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        RentLedgerEntry entry = buildDueEntry(tenantId, leaseId, dueDate);
        Lease lease = buildLeaseWithGrace(leaseId, tenantId, 5); // 5 day grace

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(lease));

        scheduler.markOverdueIfPastGrace(entry);

        // Still within grace period (3 of 5 days elapsed) — must NOT mark overdue
        verifyNoInteractions(ledgerService);
    }

    /**
     * An entry 6 days past its dueDate with a 5-day grace period MUST be flagged.
     * Confirms grace period threshold is correct under non-Nairobi JVM default.
     */
    @Test
    void markOverdueIfPastGrace_flagsOverdue_whenPastGracePeriod_underAlternativeJvmTz() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London")); // UTC+0/1

        LocalDate nairobiToday = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        // Due 6 days ago, 5-day grace — past grace threshold
        LocalDate dueDate = nairobiToday.minusDays(6);

        UUID tenantId = UUID.randomUUID();
        UUID leaseId = UUID.randomUUID();

        RentLedgerEntry entry = buildDueEntry(tenantId, leaseId, dueDate);
        Lease lease = buildLeaseWithGrace(leaseId, tenantId, 5);

        when(leaseRepository.findById(leaseId)).thenReturn(Optional.of(lease));

        UUID entryId2 = entry.getId(); // capture before passing to verify
        scheduler.markOverdueIfPastGrace(entry);

        // 6 days past due, grace expired at 5 — must mark overdue
        verify(ledgerService).markOverdue(
                eq(tenantId),
                anyString(),
                eq(entryId2),
                eq(6) // daysOverdue is from dueDate, not grace threshold
        );
    }

    private RentLedgerEntry buildDueEntry(UUID tenantId, UUID leaseId, LocalDate dueDate) {
        RentLedgerEntry entry = mock(RentLedgerEntry.class);
        when(entry.getId()).thenReturn(UUID.randomUUID());
        when(entry.getTenantId()).thenReturn(tenantId);
        when(entry.getLeaseId()).thenReturn(leaseId);
        when(entry.getDueDate()).thenReturn(dueDate);
        when(entry.getStatus()).thenReturn(RentLedgerStatus.DUE);
        return entry;
    }

    private Lease buildLeaseWithGrace(UUID leaseId, UUID tenantId, int graceDays) {
        Lease lease = mock(Lease.class);
        when(lease.getId()).thenReturn(leaseId);
        when(lease.getTenantId()).thenReturn(tenantId);
        when(lease.getGracePeriodDays()).thenReturn(graceDays);
        return lease;
    }
}
