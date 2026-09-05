package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.exception.StkPushRateLimitedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards the renter portal's STK-push endpoints (initiateRentPayment,
 * initiatePortalPayment) against a renter repeatedly tapping "pay" and
 * spamming Safaricom with overlapping STK requests for the same phone.
 * Per-user, in-process cooldown — same shape as
 * ClerkJwtAuthenticationConverter's identity cache: a ConcurrentHashMap with
 * a size-triggered sweep, deliberately not Redis/a broker (see backend
 * CLAUDE.md "Do not add Redis, Kafka or a broker" — this project keeps
 * caching/rate-limiting in-process and dependency-free at this scale). A
 * multi-instance deployment would need a shared store instead; not a
 * concern yet at this project's scale.
 *
 * This does NOT replace the DB-level idempotency guard
 * (uk_rent_transactions_tenant_external_reference) — that's what prevents a
 * successful payment from ever being double-applied. This only prevents
 * wasted/duplicate STK prompts from being sent to Safaricom in the first
 * place, which is a cost/UX concern, not a correctness one.
 */
@Component
public class StkPushRateLimiter {

    private static final Duration COOLDOWN = Duration.ofSeconds(20);
    private static final int SWEEP_THRESHOLD = 5_000;

    private final Clock clock;
    private final Map<UUID, Instant> lastInitiationAt = new ConcurrentHashMap<>();

    public StkPushRateLimiter(ObjectProvider<Clock> clockProvider) {
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    /**
     * Throws StkPushRateLimitedException if {@code userId} initiated an STK
     * push within the cooldown window; otherwise records this attempt as the
     * new "last initiation" and allows it through. Uses compute() so the
     * check-then-record is atomic per key — two concurrent requests from the
     * same renter can't both read "no previous attempt" and both proceed.
     */
    public void checkAndRecord(UUID userId) {
        Instant now = clock.instant();
        StkPushRateLimitedException[] rejection = new StkPushRateLimitedException[1];

        lastInitiationAt.compute(userId, (id, previous) -> {
            if (previous != null) {
                Duration sinceLast = Duration.between(previous, now);
                if (sinceLast.compareTo(COOLDOWN) < 0) {
                    long retryAfterSeconds = COOLDOWN.minus(sinceLast).toSeconds() + 1;
                    rejection[0] = new StkPushRateLimitedException(
                            "A payment prompt was just sent. Please wait a moment before requesting another.",
                            retryAfterSeconds
                    );
                    return previous;
                }
            }
            return now;
        });

        if (rejection[0] != null) {
            throw rejection[0];
        }

        sweepIfLarge(now);
    }

    private void sweepIfLarge(Instant now) {
        if (lastInitiationAt.size() <= SWEEP_THRESHOLD) {
            return;
        }
        lastInitiationAt.entrySet().removeIf(
                entry -> Duration.between(entry.getValue(), now).compareTo(COOLDOWN) > 0
        );
    }
}
