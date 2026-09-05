package com.rentmanager.shared.security.throttle;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A counting rate limiter over a real sliding time window.
 *
 * <h2>Replaces {@code SimpleRateLimiter}, which could not work</h2>
 * That class was referenced by nothing, and would not have helped if it had
 * been. It kept an {@code int} per key with no time component at all, so its
 * limit of 100 was a lifetime cap that latched closed permanently — nothing
 * ever called its {@code reset}. Its {@code putIfAbsent} / {@code get} /
 * {@code put} sequence was a non-atomic read-modify-write, so two concurrent
 * requests could read the same count and both write {@code count + 1},
 * undercounting under exactly the load a limiter exists to stop. And its map
 * never evicted anything.
 *
 * <p>This one keeps timestamps rather than a bare count, so the window
 * actually slides; mutates entries inside {@link ConcurrentHashMap#compute},
 * so the check and the record are one atomic step per key; and sweeps itself
 * when it grows.
 *
 * <h2>Deliberately in-process</h2>
 * No Redis, matching the project's standing rule and the existing
 * {@code StkPushRateLimiter}. A multi-instance deployment would need a shared
 * store, and until then a per-instance limit is still far better than none —
 * an attacker gets N attempts per instance rather than unlimited.
 */
@Component
public class SlidingWindowRateLimiter {

    private static final int SWEEP_THRESHOLD = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(ObjectProvider<Clock> clockProvider) {
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    /**
     * Records an attempt against {@code key} and reports whether it is within
     * the allowance.
     *
     * @return {@code true} when the attempt is permitted. A rejected attempt
     *         is <strong>not</strong> recorded, so a caller hammering the
     *         endpoint cannot push their own window further out — the
     *         opposite behaviour would make a blocked client stay blocked
     *         forever while an honest one recovers.
     */
    public boolean tryAcquire(String key, int maxAttempts, Duration window) {
        Instant now = clock.instant();
        boolean[] allowed = new boolean[1];

        hits.compute(key, (k, timestamps) -> {
            Deque<Instant> recent = timestamps == null ? new ArrayDeque<>() : timestamps;
            Instant cutoff = now.minus(window);

            while (!recent.isEmpty() && recent.peekFirst().isBefore(cutoff)) {
                recent.pollFirst();
            }

            if (recent.size() < maxAttempts) {
                recent.addLast(now);
                allowed[0] = true;
            } else {
                allowed[0] = false;
            }
            return recent.isEmpty() ? null : recent;
        });

        sweepIfLarge(now, window);
        return allowed[0];
    }

    /** Seconds until the oldest attempt in the window expires. */
    public long retryAfterSeconds(String key, Duration window) {
        Deque<Instant> recent = hits.get(key);
        if (recent == null || recent.isEmpty()) {
            return 0;
        }
        Instant oldest = recent.peekFirst();
        if (oldest == null) {
            return 0;
        }
        long seconds = Duration.between(clock.instant(), oldest.plus(window)).toSeconds();
        return Math.max(seconds, 1);
    }

    private void sweepIfLarge(Instant now, Duration window) {
        if (hits.size() <= SWEEP_THRESHOLD) {
            return;
        }
        Instant cutoff = now.minus(window);
        hits.entrySet().removeIf(entry -> {
            Deque<Instant> recent = entry.getValue();
            Instant newest = recent.peekLast();
            return newest == null || newest.isBefore(cutoff);
        });
    }
}
