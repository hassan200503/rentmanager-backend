package com.rentmanager.modules.announcement.domain.model;

import com.rentmanager.modules.announcement.domain.enums.AnnouncementChannel;
import com.rentmanager.modules.announcement.domain.enums.AnnouncementDeliveryStatus;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One broadcast delivery: one active renter x one channel. This row is
 * BOTH the fan-out work item (PENDING rows are dispatched by the
 * announcement sweep through the shared notification dispatch router) and
 * the tracking record that powers the landlord delivery stats and the
 * renter in-app read state (read_at).
 *
 * Owns its retry state with the same transitions as the Phase 5
 * notification outbox: {@code markSent()} and {@code recordFailure()} are
 * the only transitions, SENT/DELIVERED rows are never re-dispatched, and
 * the row is given up permanently after {@link #MAX_ATTEMPTS} failures.
 * SKIPPED_NO_OPTIN rows are terminal at creation - WhatsApp is never
 * attempted for a renter without captured opt-in.
 */
@Getter
public class AnnouncementDelivery {

    /** Total send attempts before the delivery is given up (1 + 2 retries). */
    public static final int MAX_ATTEMPTS = 3;

    private final UUID id;
    private final UUID tenantId;
    private final UUID announcementId;
    private final UUID renterProfileId;
    private final AnnouncementChannel channel;

    private AnnouncementDeliveryStatus status;
    private Instant sentAt;
    private Instant readAt;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String lastError;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    private AnnouncementDelivery(
            UUID id,
            UUID tenantId,
            UUID announcementId,
            UUID renterProfileId,
            AnnouncementChannel channel,
            AnnouncementDeliveryStatus status,
            Instant sentAt,
            Instant readAt,
            int attemptCount,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.announcementId = announcementId;
        this.renterProfileId = renterProfileId;
        this.channel = channel;
        this.status = status;
        this.sentAt = sentAt;
        this.readAt = readAt;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * Creates a PENDING external-channel delivery (SMS/EMAIL/WHATSAPP),
     * due immediately.
     */
    public static AnnouncementDelivery create(
            UUID tenantId,
            UUID announcementId,
            UUID renterProfileId,
            AnnouncementChannel channel
    ) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel is required");
        }
        if (channel == AnnouncementChannel.IN_APP) {
            throw new IllegalArgumentException("IN_APP deliveries are created via createDelivered");
        }
        Instant now = Instant.now();
        return new AnnouncementDelivery(
                UUID.randomUUID(),
                tenantId,
                announcementId,
                renterProfileId,
                channel,
                AnnouncementDeliveryStatus.PENDING,
                null,
                null,
                0,
                now,
                null,
                now,
                now,
                0L
        );
    }

    /**
     * Creates a terminal WhatsApp row for a renter without opt-in. The
     * row exists so the landlord delivery stats show the skip ("3 skipped
     * for WhatsApp - no opt-in on file") and so the preview counts and
     * the executed fan-out stay in lockstep. Never attempted, never
     * retried.
     */
    public static AnnouncementDelivery createSkippedNoOptIn(
            UUID tenantId,
            UUID announcementId,
            UUID renterProfileId
    ) {
        Instant now = Instant.now();
        return new AnnouncementDelivery(
                UUID.randomUUID(),
                tenantId,
                announcementId,
                renterProfileId,
                AnnouncementChannel.WHATSAPP,
                AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN,
                null,
                null,
                0,
                null,
                null,
                now,
                now,
                0L
        );
    }

    /**
     * Creates the IN_APP row as DELIVERED at creation time: the portal
     * row IS the delivery, there is no external transport to dispatch.
     * sent_at records when the announcement became visible; read_at stays
     * null until the renter opens it.
     */
    public static AnnouncementDelivery createDelivered(
            UUID tenantId,
            UUID announcementId,
            UUID renterProfileId
    ) {
        Instant now = Instant.now();
        return new AnnouncementDelivery(
                UUID.randomUUID(),
                tenantId,
                announcementId,
                renterProfileId,
                AnnouncementChannel.IN_APP,
                AnnouncementDeliveryStatus.DELIVERED,
                now,
                null,
                0,
                null,
                null,
                now,
                now,
                0L
        );
    }

    public static AnnouncementDelivery rehydrate(
            UUID id,
            UUID tenantId,
            UUID announcementId,
            UUID renterProfileId,
            AnnouncementChannel channel,
            AnnouncementDeliveryStatus status,
            Instant sentAt,
            Instant readAt,
            int attemptCount,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        return new AnnouncementDelivery(
                id, tenantId, announcementId, renterProfileId, channel,
                status, sentAt, readAt, attemptCount, nextAttemptAt, lastError,
                createdAt, updatedAt, version
        );
    }

    /**
     * Provider accepted the message. No-op on a terminal row - a
     * delivery is never re-dispatched.
     */
    public void markSent() {
        if (status == AnnouncementDeliveryStatus.SENT
                || status == AnnouncementDeliveryStatus.DELIVERED
                || status == AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN) {
            return;
        }
        this.status = AnnouncementDeliveryStatus.SENT;
        this.attemptCount++;
        this.sentAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Records a failed attempt. Retries with escalating backoff; the
     * delivery is given up permanently (terminal FAILED) after
     * MAX_ATTEMPTS failures. No-op on a terminal row.
     */
    public void recordFailure(String error) {
        if (status == AnnouncementDeliveryStatus.SENT
                || status == AnnouncementDeliveryStatus.DELIVERED
                || status == AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN) {
            return;
        }
        this.attemptCount++;
        this.lastError = truncate(error);
        this.updatedAt = Instant.now();
        if (attemptCount >= MAX_ATTEMPTS) {
            this.status = AnnouncementDeliveryStatus.FAILED;
            this.nextAttemptAt = null;
            return;
        }
        this.status = AnnouncementDeliveryStatus.FAILED;
        this.nextAttemptAt = Instant.now().plus(backoffFor(attemptCount));
    }

    /**
     * Marks the in-app row as read by the renter. Idempotent - the first
     * view wins and read_at is never overwritten.
     */
    public void markRead() {
        if (channel != AnnouncementChannel.IN_APP) {
            throw new IllegalStateException("Only IN_APP deliveries track reads");
        }
        if (readAt != null) {
            return;
        }
        this.readAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean isDue(Instant now) {
        return (status == AnnouncementDeliveryStatus.PENDING
                || status == AnnouncementDeliveryStatus.FAILED)
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
    }

    public boolean isTerminal() {
        return status == AnnouncementDeliveryStatus.SENT
                || status == AnnouncementDeliveryStatus.DELIVERED
                || status == AnnouncementDeliveryStatus.SKIPPED_NO_OPTIN
                || (status == AnnouncementDeliveryStatus.FAILED && attemptCount >= MAX_ATTEMPTS);
    }

    private static Duration backoffFor(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(15);
            default -> Duration.ofHours(1);
        };
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 500 ? error : error.substring(0, 500);
    }
}
