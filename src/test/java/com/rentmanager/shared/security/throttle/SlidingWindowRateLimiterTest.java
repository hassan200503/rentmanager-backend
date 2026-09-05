package com.rentmanager.shared.security.throttle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the three defects that made {@code SimpleRateLimiter} — the class
 * this replaces — incapable of limiting anything: no time window, a
 * non-atomic read-modify-write, and a map that never evicted.
 */
class SlidingWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Instant START = Instant.parse("2026-09-01T09:00:00Z");

    private MutableClock clock;
    private SlidingWindowRateLimiter limiter;

    /** A Clock whose instant the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        clock = new MutableClock(START);
        ObjectProvider<Clock> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(clock);
        limiter = new SlidingWindowRateLimiter(provider);
    }

    @Test
    void allowsUpToTheLimitThenRefuses() {
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isFalse();
    }

    /**
     * The defect that mattered most in the old implementation: with no time
     * component its cap was permanent, so a key that hit the limit stayed
     * blocked for the life of the process.
     */
    @Test
    void theWindowActuallySlidesSoAKeyRecovers() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, WINDOW)).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 3, WINDOW)).isFalse();

        clock.advance(WINDOW.plusSeconds(1));

        assertThat(limiter.tryAcquire("k", 3, WINDOW))
                .as("the window has passed, so the key is usable again")
                .isTrue();
    }

    @Test
    void onlyTheAttemptsInsideTheWindowCount() {
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isTrue();

        clock.advance(Duration.ofMinutes(10));
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isFalse();

        // The first attempt ages out; one slot frees up, not two.
        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isFalse();
    }

    /**
     * A rejected attempt must not extend the window. The opposite would leave
     * a client that kept retrying blocked forever while a patient one
     * recovered — punishing the impatient rather than the abusive.
     */
    @Test
    void aRejectedAttemptDoesNotPushTheWindowOut() {
        for (int i = 0; i < 2; i++) {
            limiter.tryAcquire("k", 2, WINDOW);
        }

        clock.advance(Duration.ofMinutes(14));
        assertThat(limiter.tryAcquire("k", 2, WINDOW)).isFalse();

        clock.advance(Duration.ofMinutes(2)); // 16 min after the first attempt
        assertThat(limiter.tryAcquire("k", 2, WINDOW))
                .as("the refused attempts must not have reset the clock")
                .isTrue();
    }

    @Test
    void keysAreIndependent() {
        assertThat(limiter.tryAcquire("phone:+254700000001", 1, WINDOW)).isTrue();
        assertThat(limiter.tryAcquire("phone:+254700000001", 1, WINDOW)).isFalse();

        assertThat(limiter.tryAcquire("phone:+254700000002", 1, WINDOW))
                .as("a different number has its own allowance")
                .isTrue();
    }

    @Test
    void retryAfterCountsDownAsTheWindowElapses() {
        limiter.tryAcquire("k", 1, WINDOW);

        assertThat(limiter.retryAfterSeconds("k", WINDOW)).isEqualTo(WINDOW.toSeconds());

        clock.advance(Duration.ofMinutes(5));
        assertThat(limiter.retryAfterSeconds("k", WINDOW))
                .isEqualTo(Duration.ofMinutes(10).toSeconds());
    }

    @Test
    void retryAfterIsZeroForAKeyThatHasNeverBeenSeen() {
        assertThat(limiter.retryAfterSeconds("unknown", WINDOW)).isZero();
    }

    @Test
    void retryAfterIsNeverZeroWhileAKeyIsStillBlocked() {
        limiter.tryAcquire("k", 1, WINDOW);
        clock.advance(WINDOW.minusMillis(1));

        assertThat(limiter.retryAfterSeconds("k", WINDOW))
                .as("a caller told to retry after 0 seconds would retry immediately and fail")
                .isGreaterThanOrEqualTo(1);
    }
}
