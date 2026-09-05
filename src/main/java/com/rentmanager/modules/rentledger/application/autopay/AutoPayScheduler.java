package com.rentmanager.modules.rentledger.application.autopay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily cron job that processes all enabled auto-pay settings.
 * Runs at 8:00 AM East Africa Time — deliberately before the
 * RentReminderScheduler (9:00 AM) so that auto-pay clears the
 * balance before the reminder goes out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPayScheduler {

    private static final java.time.ZoneId TZ = java.time.ZoneId.of("Africa/Nairobi");

    private final AutoPayService autoPayService;

    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Nairobi")
    public void runDaily() {
        log.info("AutoPayScheduler: starting daily run at {}", java.time.LocalTime.now(TZ));
        autoPayService.processAll();
        log.info("AutoPayScheduler: completed daily run");
    }
}