package com.rentmanager.modules.notification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Retry sweep for the notification outbox (Phase 5). Mirrors
 * {@code DisbursementRetryScheduler}: this class is NOT transactional and
 * delegates per-item work to the proxied
 * {@link NotificationDispatchSweepService} bean - one row can't block the
 * rest, and SENT rows are never touched again. Deliveries are permanently
 * GAVE_UP after {@link NotificationDelivery#MAX_ATTEMPTS} failures.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationDispatchSweepService sweepService;

    @Scheduled(fixedRateString = "${app.notification.retry-fixed-rate-ms:300000}", initialDelay = 30_000)
    public void dispatchDueNotifications() {
        try {
            sweepService.dispatchDue();
        } catch (Exception e) {
            log.error("Notification dispatch sweep failed - will retry on next sweep", e);
        }
    }
}
