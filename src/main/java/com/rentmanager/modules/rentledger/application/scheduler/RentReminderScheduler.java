package com.rentmanager.modules.rentledger.application.scheduler;

import com.rentmanager.modules.rentledger.application.reminder.RentReminderService;
import com.rentmanager.shared.observability.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Runs the rent reminder cadence once each morning.
 *
 * <h2>Position in the nightly pipeline</h2>
 * 09:00 Africa/Nairobi, deliberately after the sweeps that decide what is
 * actually owed: charges post at 02:00, the overdue sweep runs at 02:30 and
 * reconciliation at 03:00. Reminding someone before those have run would
 * quote a balance the ledger is about to change.
 *
 * <p>09:00 is also simply a civilised hour to be texted about money.
 *
 * <h2>What changed here</h2>
 * This class used to hold the entire reminder implementation: one hard-coded
 * touchpoint three days before the due date, SMS only, with no record that
 * anything had been sent — so a restart or a redeploy across the 09:00
 * boundary re-sent every message. It also carried three bugs worth naming,
 * since each is easy to reintroduce:
 *
 * <ul>
 *   <li>Its {@code @Transactional} per-entry method was invoked from a method
 *       of the same class, so Spring's proxy was bypassed and the annotation
 *       did nothing. The transaction now lives in
 *       {@code RentReminderRecorder}, a separate bean.</li>
 *   <li>It queried every entry with a due date at or before the target and
 *       then filtered in Java for exact equality — loading the full history
 *       of unpaid entries to send a handful of messages. The sweep is now
 *       bounded on both ends.</li>
 *   <li>It logged the renter's full phone number, against the project's own
 *       rule. Recipients are masked through {@code PhoneMasker} now.</li>
 * </ul>
 *
 * <p>All that remains here is the schedule. The decisions live in
 * {@link RentReminderService}, which is where they can be tested without a
 * clock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RentReminderScheduler {

    private static final ZoneId TZ = ZoneId.of("Africa/Nairobi");

    private final RentReminderService rentReminderService;
    private final BusinessMetrics metrics;

    /**
     * The zone is pinned on the cron itself rather than left to the server's
     * default. A container that comes up in UTC would otherwise fire this at
     * noon Nairobi time, and the run date computed inside would disagree with
     * the trigger that caused it around midnight.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Nairobi")
    public void sendRentReminders() {
        LocalDate runDate = LocalDate.now(TZ);

        RentReminderService.SweepResult result = rentReminderService.sweep(runDate);

        // The alert that matters here fires on the sent rate falling to
        // zero, not on an error appearing: a sweep that silently sends
        // nothing looks exactly like a quiet month.
        metrics.reminderSweepCompleted(result.sent(), result.alreadySent(), result.failed());

        log.info("RentReminderScheduler finished. runDate={} candidates={} sent={} alreadySent={} skipped={} failed={}",
                runDate,
                result.candidates(),
                result.sent(),
                result.alreadySent(),
                result.skipped(),
                result.failed());
    }
}
