package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Flags outstanding rent ledger entries as OVERDUE via the existing
 * {@link RentLedgerApplicationService#markOverdue}. Mirrors
 * {@code LeaseActionScheduler}'s structure: one tenant-agnostic sweep
 * query, then a per-entry {@code @Transactional} method.
 *
 * GRACE PERIOD: {@code Lease.gracePeriodDays} is per-lease, so it can't be
 * folded into the repository query's cutoff date — the sweep pulls every
 * DUE/PARTIALLY_PAID entry at or past its raw due date, then this method
 * fetches the owning lease and applies its grace period before deciding
 * whether to actually flag it. {@code daysOverdue} passed to
 * {@code markOverdue()} is measured from {@code dueDate} itself, not the
 * grace-adjusted threshold — that's the lateness figure a landlord
 * actually wants to see; grace period only gates WHEN the flag fires, not
 * the number once it does.
 *
 * markOverdue() on the domain entity is already idempotent (no-op if
 * already OVERDUE), so this scheduler doesn't need its own extra guard
 * against re-flagging — the status filter in the sweep query already
 * excludes anything already OVERDUE.
 *
 * REQUIRES: {@code RentLedgerEntryRepository
 * .findAllByStatusInAndDueDateLessThanEqual(List<RentLedgerStatus>, LocalDate)}
 * — new, tenant-agnostic sibling to the existing tenant-scoped
 * {@code findByTenantAndStatusInAndDueDateLessThanEqual}, mirroring how
 * {@code LeaseRepository} already carries both tenant-scoped and
 * tenant-agnostic scheduler-oriented finders side by side. Not yet
 * implemented in the JPA adapter as of this file.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentOverdueScheduler {

    private static final ZoneId TZ = ZoneId.of("Africa/Nairobi");

    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final LeaseRepository leaseRepository;
    private final RentLedgerApplicationService rentLedgerApplicationService;

    @Scheduled(cron = "0 30 2 * * *") // 2:30 AM daily — after the charge-posting sweep
    public void runDaily() {
        List<RentLedgerEntry> candidates = rentLedgerEntryRepository.findAllByStatusInAndDueDateLessThanEqual(
                List.of(RentLedgerStatus.DUE, RentLedgerStatus.PARTIALLY_PAID),
                LocalDate.now(TZ)
        );

        log.info("RentOverdueScheduler: found {} outstanding entry(ies) at or past due date", candidates.size());

        for (RentLedgerEntry entry : candidates) {
            try {
                markOverdueIfPastGrace(entry);
            } catch (Exception e) {
                log.error("Failed to evaluate overdue status for rent ledger entry id={}", entry.getId(), e);
                // continue processing remaining entries rather than aborting the batch
            }
        }
    }

    @Transactional
    public void markOverdueIfPastGrace(RentLedgerEntry entry) {
        Lease lease = leaseRepository.findById(entry.getLeaseId()).orElse(null);
        if (lease == null) {
            log.warn("No lease found for rent ledger entry id={} leaseId={}", entry.getId(), entry.getLeaseId());
            return;
        }

        int graceDays = lease.getGracePeriodDays() != null ? lease.getGracePeriodDays() : 0;
        LocalDate overdueThreshold = entry.getDueDate().plusDays(graceDays);

        if (!LocalDate.now(TZ).isAfter(overdueThreshold)) {
            return; // still within grace period
        }

        int daysOverdue = (int) ChronoUnit.DAYS.between(entry.getDueDate(), LocalDate.now(TZ));
        String correlationId = "rent-overdue-scheduler-" + entry.getId();

        rentLedgerApplicationService.markOverdue(
                entry.getTenantId(),
                correlationId,
                entry.getId(),
                daysOverdue
        );

        log.info("Marked rent ledger entry overdue. entryId={} daysOverdue={}", entry.getId(), daysOverdue);
    }
}