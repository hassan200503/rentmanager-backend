package com.rentmanager.modules.notification.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.notification.domain.model.NotificationDeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Retryable outbox row for one notification attempt on one channel
 * (Phase 5). Each channel is an independent row so one channel failing can
 * never block the others or the domain write that produced the event.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notification_deliveries")
public class NotificationDeliveryJpaEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "channel", nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationChannel channel;

    @Column(name = "recipient", nullable = false)
    private String recipient;

    @Column(name = "subject")
    private String subject;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "metadata")
    private String metadata;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationDeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public NotificationDeliveryJpaEntity(
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
            String lastError
    ) {
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
    }
}
