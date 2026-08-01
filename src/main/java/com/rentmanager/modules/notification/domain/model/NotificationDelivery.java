package com.rentmanager.modules.notification.domain.model;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox delivery (Phase 5). Owns its retry state: {@code markSent()} and
 * {@code recordFailure()} are the only transitions. The scheduler never
 * re-sends a SENT row and gives up permanently after
 * {@link #MAX_ATTEMPTS} failed attempts — a notification is never
 * retried forever.
 */
@Getter
public class NotificationDelivery {

    /** Total send attempts before the delivery is given up (1 + 2 retries). */
    public static final int MAX_ATTEMPTS = 3;

    private final UUID id;
    private final UUID tenantId;
    private final UUID eventId;
    private final NotificationChannel channel;
    private final String recipient;
    private final String subject;
    private final String message;
    private final String metadata;

    private NotificationDeliveryStatus status;
    private int attemptCount;
    private Instant nextAttemptAt;
    private String lastError;
    private final Instant createdAt;
    private Instant updatedAt;
    private Long version;

    private NotificationDelivery(
            UUID id,
            UUID tenantId,
            UUID eventId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String message,
            String metadata,
            NotificationDeliveryStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.eventId = eventId;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.metadata = metadata;
        this.status = status;
        this.attemptCount = attemptCount;
        this.nextAttemptAt = nextAttemptAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /**
     * Creates a delivery due immediately. Fails fast on missing data so a
     * broken recipient never lands in the outbox.
     */
    public static NotificationDelivery create(
            UUID tenantId,
            UUID eventId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String message,
            String metadata
    ) {
        if (channel == null) {
            throw new IllegalArgumentException("Channel is required");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Recipient is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message is required");
        }
        Instant now = Instant.now();
        return new NotificationDelivery(
                UUID.randomUUID(),
                tenantId,
                eventId,
                channel,
                recipient.trim(),
                subject,
                message,
                metadata,
                NotificationDeliveryStatus.PENDING,
                0,
                now,
                null,
                now,
                now,
                0L
        );
    }

    public static NotificationDelivery rehydrate(
            UUID id,
            UUID tenantId,
            UUID eventId,
            NotificationChannel channel,
            String recipient,
            String subject,
            String message,
            String metadata,
            NotificationDeliveryStatus status,
            int attemptCount,
            Instant nextAttemptAt,
            String lastError,
            Instant createdAt,
            Instant updatedAt,
            Long version
    ) {
        return new NotificationDelivery(
                id, tenantId, eventId, channel, recipient, subject, message, metadata,
                status, attemptCount, nextAttemptAt, lastError, createdAt, updatedAt, version
        );
    }

    public void markSent() {
        if (status == NotificationDeliveryStatus.SENT || status == NotificationDeliveryStatus.GAVE_UP) {
            return;
        }
        this.status = NotificationDeliveryStatus.SENT;
        this.attemptCount++;
        this.nextAttemptAt = null;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Records a failed attempt. Retries with escalating backoff; the
     * delivery is permanently given up after MAX_ATTEMPTS failures.
     * No-op on a terminal row (SENT/GAVE_UP) - a delivery is never
     * retried forever.
     */
    public void recordFailure(String error) {
        if (status == NotificationDeliveryStatus.SENT || status == NotificationDeliveryStatus.GAVE_UP) {
            return;
        }
        this.attemptCount++;
        this.lastError = truncate(error);
        this.updatedAt = Instant.now();
        if (attemptCount >= MAX_ATTEMPTS) {
            this.status = NotificationDeliveryStatus.GAVE_UP;
            this.nextAttemptAt = null;
            return;
        }
        this.status = NotificationDeliveryStatus.FAILED;
        this.nextAttemptAt = Instant.now().plus(backoffFor(attemptCount));
    }

    public boolean isDue(Instant now) {
        return (status == NotificationDeliveryStatus.PENDING || status == NotificationDeliveryStatus.FAILED)
                && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
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
