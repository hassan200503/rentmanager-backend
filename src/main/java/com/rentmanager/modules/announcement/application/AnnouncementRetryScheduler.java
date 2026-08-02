package com.rentmanager.modules.announcement.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Safety-net dispatch sweep for announcement deliveries. Mirrors
 * NotificationRetryScheduler, but submits to the single dispatch thread
 * instead of running inline: the immediate post-send drain and this
 * scheduled sweep share that one thread, so a PENDING row is never raced
 * into a double send. Cadence is configurable
 * ({@code app.announcement.dispatch-fixed-rate-ms}, default 10s); each
 * sweep dispatches at most batch-size rows, which together with the drain
 * pacing is the provider rate-limit throttle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementRetryScheduler {

    private final AnnouncementDispatchSweepService sweepService;
    private final AnnouncementDispatchExecutor dispatchExecutor;

    @Scheduled(fixedRateString = "${app.announcement.dispatch-fixed-rate-ms:10000}", initialDelay = 30_000)
    public void dispatchDueAnnouncements() {
        try {
            dispatchExecutor.execute(sweepService::dispatchDue);
        } catch (Exception e) {
            log.error("Announcement dispatch sweep failed - will retry on next sweep", e);
        }
    }
}
