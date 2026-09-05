package com.rentmanager.modules.rentledger.infrastructure.persistence.entity;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.rentledger.domain.enums.ReminderAudience;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Deliberately does <strong>not</strong> extend {@code BaseTenantEntity}.
 *
 * <p>{@code BaseEntity} carries {@code updated_at} and an {@code @Version}
 * column, which only make sense on a row that can change. {@code V81}'s own
 * migration comment names that as the wart it had to work around on
 * {@code rent_transactions} — "the row carries updated_at/version like any
 * other mutable entity" — and this table is append-only by trigger with no
 * legitimate update path at all. Inheriting mutability machinery here would
 * add two columns that can never move and quietly invite someone to try.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "rent_reminders")
public class RentReminderJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "rent_ledger_entry_id", nullable = false, updatable = false)
    private UUID rentLedgerEntryId;

    @Column(name = "lease_id", nullable = false, updatable = false)
    private UUID leaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "milestone", nullable = false, updatable = false, length = 32)
    private ReminderMilestone milestone;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, updatable = false, length = 16)
    private ReminderAudience audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, updatable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "recipient_masked", nullable = false, updatable = false)
    private String recipientMasked;

    @Column(name = "balance_owed_snapshot", nullable = false, updatable = false,
            precision = 19, scale = 2)
    private BigDecimal balanceOwedSnapshot;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "notification_delivery_id", updatable = false)
    private UUID notificationDeliveryId;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
