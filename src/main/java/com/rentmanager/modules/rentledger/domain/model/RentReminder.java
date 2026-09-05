package com.rentmanager.modules.rentledger.domain.model;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.rentledger.domain.enums.ReminderAudience;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * The record that one rent reminder was sent: which ledger entry it was
 * about, which point in the cadence it represented, who it went to and on
 * what channel.
 *
 * <h2>Immutable by construction and by trigger</h2>
 * Every field is {@code final} and there is no mutator. The database agrees:
 * {@code V84} installs a trigger that rejects both UPDATE and DELETE on
 * {@code rent_reminders}. A log of what you told a customer is only worth
 * having if it cannot be quietly revised afterwards — the same reasoning that
 * makes {@code rent_transactions} append-only in {@code V81}, applied to
 * evidence rather than to money.
 *
 * <h2>This is not a money object</h2>
 * {@code balanceOwedSnapshot} is the only figure here and it is deliberately
 * a snapshot, never a source. It records what the message said, so that a
 * dispute months later can be answered with the number the renter actually
 * saw rather than the number the ledger holds today. Nothing reads this class
 * to decide what anyone owes; {@link RentLedgerEntry} remains the sole
 * financial truth.
 *
 * <h2>Idempotency</h2>
 * {@code (tenantId, rentLedgerEntryId, milestone, audience, channel)} is
 * unique in the database. The sender does not check-then-insert — it inserts
 * and treats the constraint violation as "already sent". That ordering is
 * what makes the guarantee hold across two schedulers racing after a
 * redeploy, which a check-then-insert would not.
 */
public final class RentReminder {

    private final UUID id;
    private final UUID tenantId;
    private final UUID rentLedgerEntryId;
    private final UUID leaseId;
    private final ReminderMilestone milestone;
    private final ReminderAudience audience;
    private final NotificationChannel channel;
    private final String recipientMasked;
    private final BigDecimal balanceOwedSnapshot;
    private final String currency;
    private final LocalDate dueDate;
    private final UUID notificationDeliveryId;
    private final Instant sentAt;
    private final Instant createdAt;

    private RentReminder(
            UUID id,
            UUID tenantId,
            UUID rentLedgerEntryId,
            UUID leaseId,
            ReminderMilestone milestone,
            ReminderAudience audience,
            NotificationChannel channel,
            String recipientMasked,
            BigDecimal balanceOwedSnapshot,
            String currency,
            LocalDate dueDate,
            UUID notificationDeliveryId,
            Instant sentAt,
            Instant createdAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.rentLedgerEntryId = rentLedgerEntryId;
        this.leaseId = leaseId;
        this.milestone = milestone;
        this.audience = audience;
        this.channel = channel;
        this.recipientMasked = recipientMasked;
        this.balanceOwedSnapshot = balanceOwedSnapshot;
        this.currency = currency;
        this.dueDate = dueDate;
        this.notificationDeliveryId = notificationDeliveryId;
        this.sentAt = sentAt;
        this.createdAt = createdAt;
    }

    /**
     * Records a reminder that is being sent now.
     *
     * @param recipientMasked already masked by the caller — see
     *                        {@code PhoneMasker}. The unmasked address lives
     *                        on {@code notification_deliveries}, which is the
     *                        row that needs it in order to send. An audit log
     *                        does not need to be a second copy of everyone's
     *                        phone number.
     */
    public static RentReminder record(
            UUID tenantId,
            UUID rentLedgerEntryId,
            UUID leaseId,
            ReminderMilestone milestone,
            ReminderAudience audience,
            NotificationChannel channel,
            String recipientMasked,
            BigDecimal balanceOwedSnapshot,
            String currency,
            LocalDate dueDate,
            UUID notificationDeliveryId,
            Instant sentAt
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(rentLedgerEntryId, "rentLedgerEntryId");
        Objects.requireNonNull(leaseId, "leaseId");
        Objects.requireNonNull(milestone, "milestone");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(sentAt, "sentAt");

        if (recipientMasked == null || recipientMasked.isBlank()) {
            throw new IllegalArgumentException("recipientMasked is required");
        }
        if (balanceOwedSnapshot == null || balanceOwedSnapshot.signum() < 0) {
            throw new IllegalArgumentException(
                    "balanceOwedSnapshot must be present and non-negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }

        return new RentReminder(
                UUID.randomUUID(), tenantId, rentLedgerEntryId, leaseId,
                milestone, audience, channel, recipientMasked,
                balanceOwedSnapshot, currency, dueDate,
                notificationDeliveryId, sentAt, Instant.now()
        );
    }

    /** Rebuilds a persisted reminder. Performs no validation by design. */
    public static RentReminder rehydrate(
            UUID id,
            UUID tenantId,
            UUID rentLedgerEntryId,
            UUID leaseId,
            ReminderMilestone milestone,
            ReminderAudience audience,
            NotificationChannel channel,
            String recipientMasked,
            BigDecimal balanceOwedSnapshot,
            String currency,
            LocalDate dueDate,
            UUID notificationDeliveryId,
            Instant sentAt,
            Instant createdAt
    ) {
        return new RentReminder(
                id, tenantId, rentLedgerEntryId, leaseId, milestone, audience,
                channel, recipientMasked, balanceOwedSnapshot, currency,
                dueDate, notificationDeliveryId, sentAt, createdAt
        );
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getRentLedgerEntryId() { return rentLedgerEntryId; }
    public UUID getLeaseId() { return leaseId; }
    public ReminderMilestone getMilestone() { return milestone; }
    public ReminderAudience getAudience() { return audience; }
    public NotificationChannel getChannel() { return channel; }
    public String getRecipientMasked() { return recipientMasked; }
    public BigDecimal getBalanceOwedSnapshot() { return balanceOwedSnapshot; }
    public String getCurrency() { return currency; }
    public LocalDate getDueDate() { return dueDate; }
    public UUID getNotificationDeliveryId() { return notificationDeliveryId; }
    public Instant getSentAt() { return sentAt; }
    public Instant getCreatedAt() { return createdAt; }
}
