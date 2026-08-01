package com.rentmanager.modules.notification.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5: outbox retry state machine. SENT rows must never be touched
 * again, failures retry with escalating backoff, and a delivery is
 * permanently given up after MAX_ATTEMPTS failures - never retried
 * forever.
 */
class NotificationDeliveryTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @Test
    void createdDeliveryIsPendingAndDueImmediately() {
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "+254712345678",
                null, "Hello", null);

        assertEquals(NotificationDeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttemptCount());
        assertTrue(delivery.isDue(Instant.now()));
        assertEquals(tenantId, delivery.getTenantId());
        assertEquals(eventId, delivery.getEventId());
    }

    @Test
    void rejectsMissingChannelRecipientOrMessage() {
        assertThrows(IllegalArgumentException.class, () -> NotificationDelivery.create(
                tenantId, eventId, null, "+254712345678", null, "Hello", null));
        assertThrows(IllegalArgumentException.class, () -> NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "  ", null, "Hello", null));
        assertThrows(IllegalArgumentException.class, () -> NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "+254712345678", null, "", null));
    }

    @Test
    void markSentClearsRetryStateAndIsNeverResent() {
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.EMAIL, "a@b.c", "Subj", "Body", null);

        delivery.markSent();

        assertEquals(NotificationDeliveryStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNull(delivery.getNextAttemptAt());
        assertFalse(delivery.isDue(Instant.now()));

        Instant before = delivery.getUpdatedAt();
        delivery.markSent();
        assertEquals(1, delivery.getAttemptCount(), "SENT rows are never re-sent");
        assertTrue(delivery.getUpdatedAt().equals(before));
    }

    @Test
    void firstFailureRetriesWithinOneMinute() {
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "+254712345678", null, "Hi", null);

        delivery.recordFailure("provider down");

        assertEquals(NotificationDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getNextAttemptAt());
        assertFalse(delivery.getNextAttemptAt().isAfter(Instant.now().plus(61, ChronoUnit.SECONDS)));
        assertEquals("provider down", delivery.getLastError());
    }

    @Test
    void failedDeliveryIsDueOnceBackoffElapses() {
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "+254712345678", null, "Hi", null);

        delivery.recordFailure("attempt 1");

        assertFalse(delivery.isDue(Instant.now()), "not due while backing off");

        NotificationDelivery retryReady = NotificationDelivery.rehydrate(
                delivery.getId(), tenantId, eventId, NotificationChannel.SMS,
                "+254712345678", null, "Hi", null,
                NotificationDeliveryStatus.FAILED, 1,
                Instant.now().minus(1, ChronoUnit.SECONDS),
                "attempt 1", Instant.now().minusSeconds(60), Instant.now().minusSeconds(60), 2L);

        assertTrue(retryReady.isDue(Instant.now()), "FAILED row is due once the backoff elapsed");
    }

    @Test
    void secondFailureBacksOffToFifteenMinutes() {
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "+254712345678", null, "Hi", null);

        delivery.recordFailure("attempt 1");
        delivery.recordFailure("attempt 2");

        assertEquals(2, delivery.getAttemptCount());
        assertFalse(delivery.getNextAttemptAt().isAfter(Instant.now().plus(16, ChronoUnit.MINUTES)));
    }

    @Test
    void givesUpPermanentlyAfterMaxAttempts() {
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "+254712345678", null, "Hi", null);

        delivery.recordFailure("a1");
        delivery.recordFailure("a2");
        delivery.recordFailure("a3");

        assertEquals(NotificationDeliveryStatus.GAVE_UP, delivery.getStatus());
        assertEquals(3, delivery.getAttemptCount());
        assertNull(delivery.getNextAttemptAt());
        assertFalse(delivery.isDue(Instant.now()));
    }

    @Test
    void gaveUpDeliveryIsNeverRetried() {
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "+254712345678", null, "Hi", null);
        delivery.recordFailure("a1");
        delivery.recordFailure("a2");
        delivery.recordFailure("a3");

        delivery.recordFailure("a4");

        assertEquals(3, delivery.getAttemptCount(), "no more attempts after giving up");
        assertEquals(NotificationDeliveryStatus.GAVE_UP, delivery.getStatus());
    }

    @Test
    void truncatesLongErrorToColumnLimit() {
        NotificationDelivery delivery = NotificationDelivery.create(
                tenantId, eventId, NotificationChannel.SMS, "+254712345678", null, "Hi", null);

        delivery.recordFailure("x".repeat(1000));

        assertEquals(500, delivery.getLastError().length());
    }
}
