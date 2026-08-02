package com.rentmanager.modules.announcement.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kicks the announcement fan-out off immediately after a send, without
 * blocking the HTTP request: the delivery rows have been persisted by the
 * broadcast listener, and this drains them in paced batches on the single
 * dispatch thread (batch-size rows, then drain-delay-ms, then the next
 * batch) until the queue is empty. The scheduled sweep remains the safety
 * net for anything this drain misses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementDispatchTrigger {

    private final AnnouncementDispatchSweepService sweepService;
    private final AnnouncementDispatchExecutor dispatchExecutor;
    private final AnnouncementProperties properties;

    public void dispatchNow() {
        CompletableFuture.runAsync(this::drain, dispatchExecutor::execute).exceptionally(ex -> {
            log.error("Announcement drain failed - the scheduled sweep will take over", ex);
            return null;
        });
    }

    private void drain() {
        while (true) {
            int dispatched;
            try {
                dispatched = sweepService.dispatchDue();
            } catch (Exception e) {
                log.error("Announcement drain pass failed - the scheduled sweep will take over", e);
                return;
            }
            if (dispatched == 0) {
                return;
            }
            try {
                Thread.sleep(properties.getDrainDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
