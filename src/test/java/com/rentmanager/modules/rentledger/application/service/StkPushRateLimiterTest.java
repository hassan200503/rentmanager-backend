package com.rentmanager.modules.rentledger.application.service;

import com.rentmanager.modules.rentledger.domain.exception.StkPushRateLimitedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StkPushRateLimiter. No @Mock/@InjectMocks/MockitoExtension
 * per project convention — manual mock() construction, deterministic time
 * via a hand-rolled Clock (mirrors ClerkJwtAuthenticationConverterTest's
 * MutableClock, the established pattern for this codebase's in-process,
 * TTL/cooldown-based caches).
 */
class StkPushRateLimiterTest {

    private static ObjectProvider<Clock> clockProvider(Clock clock) {
        @SuppressWarnings("unchecked")
        ObjectProvider<Clock> provider = mock(ObjectProvider.class);
        lenient().when(provider.getIfAvailable(any())).thenReturn(clock);
        return provider;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @Test
    void firstAttempt_isAlwaysAllowed() {
        StkPushRateLimiter limiter = new StkPushRateLimiter(clockProvider(Clock.fixed(Instant.now(), ZoneOffset.UTC)));

        assertDoesNotThrow(() -> limiter.checkAndRecord(UUID.randomUUID()));
    }

    @Test
    void secondAttemptWithinCooldown_isRejected() {
        MutableClock clock = new MutableClock(Instant.now());
        StkPushRateLimiter limiter = new StkPushRateLimiter(clockProvider(clock));
        UUID userId = UUID.randomUUID();

        limiter.checkAndRecord(userId);
        clock.advance(Duration.ofSeconds(5)); // well under the 20s cooldown

        StkPushRateLimitedException ex = assertThrows(
                StkPushRateLimitedException.class,
                () -> limiter.checkAndRecord(userId)
        );
        assertEquals(16, ex.getRetryAfterSeconds()); // 20s cooldown - 5s elapsed, rounded up +1
    }

    @Test
    void attemptAfterCooldownElapses_isAllowed() {
        MutableClock clock = new MutableClock(Instant.now());
        StkPushRateLimiter limiter = new StkPushRateLimiter(clockProvider(clock));
        UUID userId = UUID.randomUUID();

        limiter.checkAndRecord(userId);
        clock.advance(Duration.ofSeconds(21)); // past the 20s cooldown

        assertDoesNotThrow(() -> limiter.checkAndRecord(userId));
    }

    @Test
    void differentUsers_areRateLimitedIndependently() {
        MutableClock clock = new MutableClock(Instant.now());
        StkPushRateLimiter limiter = new StkPushRateLimiter(clockProvider(clock));

        limiter.checkAndRecord(UUID.randomUUID());
        // A different renter's very next attempt is unaffected by the first renter's cooldown.
        assertDoesNotThrow(() -> limiter.checkAndRecord(UUID.randomUUID()));
    }

    @Test
    void rejectedAttempt_doesNotExtendTheCooldownWindow() {
        MutableClock clock = new MutableClock(Instant.now());
        StkPushRateLimiter limiter = new StkPushRateLimiter(clockProvider(clock));
        UUID userId = UUID.randomUUID();

        limiter.checkAndRecord(userId);
        clock.advance(Duration.ofSeconds(5));
        assertThrows(StkPushRateLimitedException.class, () -> limiter.checkAndRecord(userId));

        // Advance to just past the ORIGINAL cooldown (20s from the first call, i.e.
        // 15s more from here) -- if the rejected attempt at t=5s had incorrectly
        // reset the window, this would still be blocked.
        clock.advance(Duration.ofSeconds(15));
        assertDoesNotThrow(() -> limiter.checkAndRecord(userId));
    }
}
