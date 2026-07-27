package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Posts each active lease's recurring monthly rent charge via the existing
 * {@link RentLedgerApplicationService#postCharge}. Mirrors
 * {@code LeaseActionScheduler}'s structure: one tenant-agnostic sweep
 * query, then a per-lease {@code @Transactional} method with its own
 * idempotency guard so one lease's failure doesn't roll back the batch.
 *
 * BILLING MODEL (confirmed against {@code LeaseActivationOrchestrator}, not
 * invented here): periods are calendar-month aligned, not anchored to the
 * lease's day-of-month. The opening period runs from
 * {@code lease.getStartDate()} to the end of that same calendar month
 * (prorated if start isn't the 1st) — {@code LeaseActivationOrchestrator}
 * already posts that one at activation time. Every period after that is a
 * full calendar month: 1st to last day. {@code dueDate} equals
 * {@code billingPeriodStart} for every period, matching
 * {@code LeaseActivationOrchestrator}'s "rent is due immediately at period
 * start" product decision.
 *
 * CATCH-UP BEHAVIOR: resumes from the lease's most recently posted entry
 * rather than only ever checking the current month, so a scheduler outage
 * of several days/months self-heals on the next run instead of silently
 * skipping charges. {@code postCharge}'s own
 * {@code (leaseId, billingPeriodStart)} idempotency check is the final
 * backstop if this method is ever re-triggered for a period it already
 * posted.
 *
 * REQUIRES: {@code LeaseRepository.findAllByStatusIn(List<LeaseStatus>)}
 * and {@code RentLedgerEntryRepository.findLatestByLeaseId(UUID)} — both
 * new, tenant-agnostic, mirroring the existing scheduler-oriented finder
 * pattern already on both interfaces. Not yet implemented in the JPA
 * adapters as of this file.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentChargeScheduler {

    private final LeaseRepository leaseRepository;
    private final RentLedgerEntryRepository rentLedgerEntryRepository;
    private final RentLedgerApplicationService rentLedgerApplicationService;

    @Scheduled(cron = "0 0 2 * * *") // 2:00 AM daily — after lease activation (1:00) and expiry (1:30) sweeps
    public void runDaily() {
        List<Lease> leases = leaseRepository.findAllByStatusIn(
                List.of(LeaseStatus.ACTIVE, LeaseStatus.RENEWED)
        );

        log.info("RentChargeScheduler: found {} active/renewed lease(s) to check for charges", leases.size());

        for (Lease lease : leases) {
            try {
                postDueChargesForLease(lease);
            } catch (Exception e) {
                log.error("Failed to post rent charge(s) for lease id={}", lease.getId(), e);
                // continue processing remaining leases rather than aborting the batch
            }
        }
    }

    /**
     * Each lease gets its own transaction, same isolation pattern as
     * {@code LeaseActionScheduler.activateOne()}. Posts every calendar-month
     * period between the lease's last posted entry and the current month,
     * inclusive — usually zero or one period per run, more only if the
     * scheduler missed prior runs.
     */
    @Transactional
    public void postDueChargesForLease(Lease lease) {
        YearMonth openingMonth = YearMonth.from(lease.getStartDate());
        YearMonth currentMonth = YearMonth.now(ZoneId.of("Africa/Nairobi"));

        Optional<RentLedgerEntry> latest = rentLedgerEntryRepository.findLatestByLeaseId(lease.getId());

        // Next month to post is the one after whatever was last posted —
        // or the month right after the opening month if, defensively,
        // nothing has been posted for this lease yet at all.
        YearMonth nextMonth = latest
                .map(entry -> YearMonth.from(entry.getBillingPeriodStart()).plusMonths(1))
                .orElse(openingMonth.plusMonths(1));

        while (!nextMonth.isAfter(currentMonth)) {
            LocalDate periodStart = nextMonth.atDay(1);
            LocalDate periodEnd = nextMonth.atEndOfMonth();
            LocalDate dueDate = periodStart;

            String correlationId = "rent-charge-scheduler-" + lease.getId() + "-" + nextMonth;

            rentLedgerApplicationService.postCharge(
                    lease.getTenantId(),
                    correlationId,
                    lease.getId(),
                    periodStart,
                    periodEnd,
                    dueDate
            );

            log.info("Posted rent charge. leaseId={} period={}", lease.getId(), nextMonth);

            nextMonth = nextMonth.plusMonths(1);
        }
    }
}