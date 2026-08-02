package com.rentmanager.modules.announcement.application;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The single dispatch thread for announcement fan-out. EVERY path that
 * dispatches announcement deliveries funnels through this one thread -
 * the immediate post-send drain and the safety-net scheduled sweeps
 * alike - so two passes can never race the same PENDING row into a
 * double send. A broadcast of hundreds of rows therefore streams out
 * one batch at a time, never in a burst (see AnnouncementRetryScheduler
 * and AnnouncementBroadcastListener).
 */
@Slf4j
@Component
public class AnnouncementDispatchExecutor {

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "announcement-dispatch");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Queues a dispatch pass; passes run strictly one at a time in FIFO
     * order on the single dispatch thread.
     */
    public void execute(Runnable task) {
        executor.execute(task);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
