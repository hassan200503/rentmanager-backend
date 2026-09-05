package com.rentmanager.modules.announcement.domain.model;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementPriority;
import com.rentmanager.modules.announcement.domain.events.AnnouncementCreated;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementDomainTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();

    @Test
    void create_requiresAuthor() {
        assertThrows(IllegalArgumentException.class, () -> Announcement.create(
                tenantId, null, "Water shutoff tomorrow", AnnouncementPriority.INFO,
                EnumSet.allOf(AnnouncementChannel.class), null, "corr"));
    }

    @Test
    void create_requiresMessage() {
        assertThrows(IllegalArgumentException.class, () -> Announcement.create(
                tenantId, authorId, "   ", AnnouncementPriority.INFO,
                EnumSet.allOf(AnnouncementChannel.class), null, "corr"));
    }

    @Test
    void create_requiresPriority() {
        assertThrows(IllegalArgumentException.class, () -> Announcement.create(
                tenantId, authorId, "Water shutoff tomorrow", null,
                EnumSet.allOf(AnnouncementChannel.class), null, "corr"));
    }

    @Test
    void create_forcesInAppChannel_always() {
        Announcement announcement = Announcement.create(
                tenantId, authorId, "Rent due Friday", AnnouncementPriority.URGENT,
                Set.of(AnnouncementChannel.SMS, AnnouncementChannel.WHATSAPP), null, "corr");

        assertTrue(announcement.getChannels().contains(AnnouncementChannel.IN_APP));
        assertTrue(announcement.getChannels().contains(AnnouncementChannel.SMS));
        assertTrue(announcement.getChannels().contains(AnnouncementChannel.WHATSAPP));
        assertEquals(3, announcement.getChannels().size());
    }

    @Test
    void create_withNullChannels_stillDeliversInApp() {
        Announcement announcement = Announcement.create(
                tenantId, authorId, "Hello", AnnouncementPriority.INFO, null, null, "corr");

        assertEquals(Set.of(AnnouncementChannel.IN_APP), announcement.getChannels());
    }

    @Test
    void create_registersAnnouncementCreatedEvent() {
        Announcement announcement = Announcement.create(
                tenantId, authorId, "Hello", AnnouncementPriority.INFO,
                EnumSet.allOf(AnnouncementChannel.class), null, "corr-123");

        assertTrue(announcement.hasDomainEvents());
        AnnouncementCreated event = (AnnouncementCreated) announcement.pullDomainEvents().get(0);
        assertEquals(tenantId, event.getTenantId());
        assertEquals(announcement.getId(), event.getAnnouncementId());
        assertEquals("corr-123", event.getCorrelationId());
        assertTrue(event.getChannels().contains(AnnouncementChannel.IN_APP));
    }

    @Test
    void isExpired_trueWhenExpiresAtInPast() {
        Announcement announcement = Announcement.create(
                tenantId, authorId, "Hello", AnnouncementPriority.INFO,
                null, Instant.now().minusSeconds(60), "corr");

        assertTrue(announcement.isExpired(Instant.now()));
    }

    @Test
    void isExpired_falseWhenNoExpiryOrInFuture() {
        Announcement noExpiry = Announcement.create(
                tenantId, authorId, "Hello", AnnouncementPriority.INFO, null, null, "corr");
        Announcement future = Announcement.create(
                tenantId, authorId, "Hello", AnnouncementPriority.INFO,
                null, Instant.now().plusSeconds(3600), "corr");

        assertFalse(noExpiry.isExpired(Instant.now()));
        assertFalse(future.isExpired(Instant.now()));
    }

    // ---------------------------------------------------------------
    // AnnouncementDelivery lifecycle
    // ---------------------------------------------------------------

    @Test
    void delivery_create_isPendingAndDueImmediately() {
        AnnouncementDelivery delivery = AnnouncementDelivery.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), AnnouncementChannel.SMS);

        assertEquals(AnnouncementDeliveryStatus.PENDING, delivery.getStatus());
        assertTrue(delivery.isDue(Instant.now()));
        assertFalse(delivery.isTerminal());
    }

    @Test
    void delivery_create_rejectsInAppChannel() {
        assertThrows(IllegalArgumentException.class, () -> AnnouncementDelivery.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), AnnouncementChannel.IN_APP));
    }

    @Test
    void delivery_markSent_setsStatusAndTimestampOnce() {
        AnnouncementDelivery delivery = AnnouncementDelivery.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), AnnouncementChannel.SMS);

        delivery.markSent();

        assertEquals(AnnouncementDeliveryStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertNotNull(delivery.getSentAt());
        assertFalse(delivery.isDue(Instant.now()));

        delivery.markSent();
        assertEquals(1, delivery.getAttemptCount(), "SENT is terminal - never re-sent");
    }

    @Test
    void delivery_recordFailure_backsOffThenGivesUpAfterThreeAttempts() {
        AnnouncementDelivery delivery = AnnouncementDelivery.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), AnnouncementChannel.EMAIL);

        delivery.recordFailure("smtp down");
        assertEquals(AnnouncementDeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        Instant firstRetry = delivery.getNextAttemptAt();
        assertBackoffIsAbout(Duration.ofMinutes(1), firstRetry);

        delivery.recordFailure("smtp down again");
        assertEquals(2, delivery.getAttemptCount());
        assertBackoffIsAbout(Duration.ofMinutes(15), delivery.getNextAttemptAt());

        delivery.recordFailure("smtp down thrice");
        assertEquals(3, delivery.getAttemptCount());
        assertEquals(AnnouncementDeliveryStatus.FAILED, delivery.getStatus());
        assertNull(delivery.getNextAttemptAt(), "exhausted - no more retries");
        assertTrue(delivery.isTerminal());
    }

    @Test
    void delivery_failedIsDueOnlyAfterBackoffElapsed() {
        AnnouncementDelivery delivery = AnnouncementDelivery.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), AnnouncementChannel.SMS);

        delivery.recordFailure("provider down");

        assertFalse(delivery.isDue(Instant.now()), "not due before backoff elapses");
        assertTrue(delivery.isDue(delivery.getNextAttemptAt().plusSeconds(1)), "due after backoff elapses");
    }

    @Test
    void delivery_skippedNoOptIn_isTerminalAndNeverDue() {
        AnnouncementDelivery delivery = AnnouncementDelivery.createSkippedNoOptIn(
                tenantId, UUID.randomUUID(), UUID.randomUUID());

        assertEquals(AnnouncementChannel.WHATSAPP, delivery.getChannel());
        assertEquals(AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN, delivery.getStatus());
        assertFalse(delivery.isDue(Instant.now()));
        assertTrue(delivery.isTerminal());

        delivery.recordFailure("should never be attempted");
        assertEquals(0, delivery.getAttemptCount(), "terminal - failures are never recorded");
        assertEquals(AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN, delivery.getStatus());
    }

    @Test
    void delivery_inApp_isDeliveredAtCreationWithNullReadAt() {
        AnnouncementDelivery delivery = AnnouncementDelivery.createDelivered(
                tenantId, UUID.randomUUID(), UUID.randomUUID());

        assertEquals(AnnouncementChannel.IN_APP, delivery.getChannel());
        assertEquals(AnnouncementDeliveryStatus.DELIVERED, delivery.getStatus());
        assertNotNull(delivery.getSentAt(), "sent_at records when the announcement became visible");
        assertNull(delivery.getReadAt());
        assertFalse(delivery.isDue(Instant.now()));
    }

    @Test
    void delivery_markRead_setsReadAtOnce() {
        AnnouncementDelivery delivery = AnnouncementDelivery.createDelivered(
                tenantId, UUID.randomUUID(), UUID.randomUUID());

        delivery.markRead();
        assertNotNull(delivery.getReadAt());
        Instant firstRead = delivery.getReadAt();

        delivery.markRead();
        assertEquals(firstRead, delivery.getReadAt(), "first view wins - read_at is never overwritten");
    }

    @Test
    void delivery_markRead_rejectsNonInAppRows() {
        AnnouncementDelivery delivery = AnnouncementDelivery.create(
                tenantId, UUID.randomUUID(), UUID.randomUUID(), AnnouncementChannel.SMS);

        assertThrows(IllegalStateException.class, delivery::markRead);
    }

    /**
     * Asserts a backoff lands about {@code expected} from now.
     *
     * <p>These assertions used to compare exactly:
     * {@code assertEquals(Duration.ofMinutes(1), Duration.between(Instant.now(), retry))}.
     * {@code recordFailure} computes {@code nextAttemptAt} from its OWN
     * {@code Instant.now()}, so the test's later {@code Instant.now()} is
     * already a fraction of a millisecond ahead and the difference is
     * 1 minute MINUS that gap. It passed only while both calls happened to
     * land in the same clock tick, and failed the moment they did not —
     * observed as {@code expected: <PT1M> but was: <PT59.9994773S>}.
     *
     * <p>A tolerance is the honest assertion here: the property under test is
     * "the backoff is a minute, then fifteen", not "two wall-clock reads are
     * identical". Ten seconds is wide enough that a slow or loaded CI machine
     * never reddens the build, and far too narrow to let a wrong backoff
     * tier (1m vs 15m) slip through.
     */
    private static void assertBackoffIsAbout(Duration expected, Instant nextAttemptAt) {
        Duration actual = Duration.between(Instant.now(), nextAttemptAt);
        Duration slack = Duration.ofSeconds(10);
        assertTrue(
                actual.compareTo(expected.minus(slack)) >= 0
                        && actual.compareTo(expected.plus(slack)) <= 0,
                () -> "expected a backoff of about " + expected + " but was " + actual);
    }
}
