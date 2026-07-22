package com.rentmanager.modules.reservation.application.service;

import com.rentmanager.modules.reservation.domain.enums.PaymentIntentStatus;
import com.rentmanager.modules.reservation.domain.model.PaymentIntent;
import com.rentmanager.modules.reservation.domain.repository.PaymentIntentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Safety net for PaymentIntents whose M-Pesa callback is never delivered at
 * all — as opposed to delivered-and-reporting-failure, which
 * MpesaCallbackService already handles directly and immediately. Causes
 * include a dropped webhook (ngrok tunnel down, server restart mid-flight)
 * or a customer who simply abandons the STK prompt on their phone without
 * Safaricom's own timeout callback ever reaching us.
 *
 * This class itself is NOT @Transactional. The actual DB work happens in
 * PaymentIntentExpirySweepService, invoked as calls on a separate proxied
 * bean — never as internal method calls on `this` — so each stale intent
 * is processed in its own genuine transaction. One bad row (e.g. its unit
 * was deleted) can't block the rest of the batch — logged and skipped
 * instead, and retried on the next sweep cycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentExpiryScheduler {

    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentIntentExpirySweepService sweepService;

    // TODO: confirm desired timeout — 10 minutes is a placeholder. Should
    // comfortably exceed the longest realistic time a user takes to enter
    // their M-Pesa PIN, plus Safaricom's own callback delivery latency.
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);

    @Scheduled(fixedRate = 5 * 60 * 1000) // every 5 minutes
    public void expireStalePaymentIntents() {
        Instant cutoff = Instant.now().minus(STALE_AFTER);

        List<PaymentIntent> stale = paymentIntentRepository
                .findByStatusAndCreatedAtBefore(PaymentIntentStatus.PENDING, cutoff);

        if (stale.isEmpty()) {
            return;
        }

        log.info("Stale PaymentIntent sweep found {} candidate(s) older than {}", stale.size(), cutoff);

        for (PaymentIntent intent : stale) {
            try {
                sweepService.expireOne(intent.getId());
            } catch (Exception e) {
                log.error("Failed to expire stale PaymentIntent — will retry on next sweep. " +
                        "paymentIntentId={}", intent.getId(), e);
            }
        }
    }

    /**
     * Orphaned-unit recovery sweep — runs alongside the stale-intent sweep.
     *
     * Finds PaymentIntents that reached a terminal failure state
     * (FAILED/EXPIRED) but whose unit is still PENDING_PAYMENT because
     * MpesaCallbackService's releaseUnitIfPendingPayment() swallowed a
     * transient error. Without this, such units would be stuck forever.
     *
     * Staggered 30 seconds after the stale-intent sweep to reduce DB
     * contention in the unlikely event both find candidates at once.
     */
    @Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 30_000) // 30s after startup, then every 5 min
    public void releaseOrphanedUnits() {
        Instant cutoff = Instant.now().minus(STALE_AFTER);

        List<PaymentIntent> terminal = paymentIntentRepository
                .findByStatusInAndCreatedAtBefore(
                        List.of(PaymentIntentStatus.FAILED, PaymentIntentStatus.EXPIRED),
                        cutoff);

        if (terminal.isEmpty()) {
            return;
        }

        log.info("Orphaned-unit sweep found {} candidate(s) older than {}", terminal.size(), cutoff);

        for (PaymentIntent intent : terminal) {
            try {
                sweepService.releaseOrphanedUnit(intent.getId());
            } catch (Exception e) {
                log.error("Failed to release orphaned unit — will retry on next sweep. " +
                        "paymentIntentId={}", intent.getId(), e);
            }
        }
    }
}