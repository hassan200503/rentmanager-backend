package com.rentmanager.modules.rentledger.domain.model;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One landlord's decision about a single point in the reminder cadence:
 * whether it fires at all, on which channels the renter hears about it, and
 * whether the landlord is copied.
 *
 * <h2>Why this is configurable at all</h2>
 * SMS costs real money per message in Kenya and the landlord pays it. A
 * six-touchpoint cadence billed to them without their say is not a feature,
 * so the cadence is theirs to set. Email is free and on by default; SMS is
 * reserved for the milestones most likely to actually produce a payment.
 *
 * <h2>Defaults exist in two places on purpose</h2>
 * {@code V85} seeds a row per milestone for every landlord that existed when
 * it ran. {@link #defaultFor} is the same table expressed in code, and it is
 * what a landlord created afterwards resolves to until they save a
 * preference. Keeping both means a missed seed degrades to the intended
 * behaviour instead of to silence — the failure mode for a reminder system
 * being that nobody is ever told anything, which is invisible until a
 * landlord asks why collections dropped.
 *
 * <p>The two must stay in step. If you change one, change the other.
 */
public final class RentReminderPolicy {

    private final UUID id;
    private final UUID tenantId;
    private final ReminderMilestone milestone;
    private final boolean enabled;
    private final boolean smsEnabled;
    private final boolean emailEnabled;
    private final boolean whatsappEnabled;
    private final boolean notifyLandlord;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RentReminderPolicy(
            UUID id,
            UUID tenantId,
            ReminderMilestone milestone,
            boolean enabled,
            boolean smsEnabled,
            boolean emailEnabled,
            boolean whatsappEnabled,
            boolean notifyLandlord,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.tenantId = tenantId;
        this.milestone = milestone;
        this.enabled = enabled;
        this.smsEnabled = smsEnabled;
        this.emailEnabled = emailEnabled;
        this.whatsappEnabled = whatsappEnabled;
        this.notifyLandlord = notifyLandlord;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * The default policy for a milestone, used when a landlord has no stored
     * preference. Mirrors the seed in {@code V85} exactly.
     *
     * <ul>
     *   <li>{@code T_MINUS_7} — off. A week out nobody has forgotten yet.</li>
     *   <li>{@code T_MINUS_3} — email. The useful nudge, free to send.</li>
     *   <li>{@code DUE_TODAY} — email and SMS. Most likely to produce a payment.</li>
     *   <li>{@code OVERDUE_1} — email. One day late is usually timing, not
     *       refusal; do not spend an SMS and do not alarm anyone.</li>
     *   <li>{@code OVERDUE_3} — email and SMS. Now it is a pattern.</li>
     *   <li>{@code OVERDUE_7} — email, SMS, and the landlord is told.</li>
     * </ul>
     */
    public static RentReminderPolicy defaultFor(UUID tenantId, ReminderMilestone milestone) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(milestone, "milestone");

        boolean enabled = milestone != ReminderMilestone.T_MINUS_7;
        boolean sms = milestone == ReminderMilestone.DUE_TODAY
                || milestone == ReminderMilestone.OVERDUE_3
                || milestone == ReminderMilestone.OVERDUE_7;
        boolean landlord = milestone == ReminderMilestone.OVERDUE_7;

        return new RentReminderPolicy(
                null, tenantId, milestone, enabled, sms, true, false, landlord, null, null);
    }

    public static RentReminderPolicy rehydrate(
            UUID id,
            UUID tenantId,
            ReminderMilestone milestone,
            boolean enabled,
            boolean smsEnabled,
            boolean emailEnabled,
            boolean whatsappEnabled,
            boolean notifyLandlord,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new RentReminderPolicy(
                id, tenantId, milestone, enabled, smsEnabled, emailEnabled,
                whatsappEnabled, notifyLandlord, createdAt, updatedAt);
    }

    /**
     * The channels the renter should be messaged on at this milestone.
     * Empty when the milestone is disabled or no channel is switched on —
     * both of which are legitimate configurations, not errors.
     */
    public List<NotificationChannel> renterChannels() {
        List<NotificationChannel> channels = new ArrayList<>(3);
        if (!enabled) {
            return List.of();
        }
        if (emailEnabled) {
            channels.add(NotificationChannel.EMAIL);
        }
        if (smsEnabled) {
            channels.add(NotificationChannel.SMS);
        }
        if (whatsappEnabled) {
            channels.add(NotificationChannel.WHATSAPP);
        }
        return List.copyOf(channels);
    }

    /**
     * The channels the landlord should be messaged on at this milestone.
     *
     * <p>SMS only, and deliberately so: a landlord being told a tenant is a
     * week late needs it to arrive, and this is the one milestone where the
     * default spends an SMS on them. It is gated by {@code notifyLandlord}
     * so a landlord managing hundreds of units can switch it off rather than
     * receive a message per overdue tenant per month.
     */
    public List<NotificationChannel> landlordChannels() {
        if (!enabled || !notifyLandlord) {
            return List.of();
        }
        return List.of(NotificationChannel.SMS);
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public ReminderMilestone getMilestone() { return milestone; }
    public boolean isEnabled() { return enabled; }
    public boolean isSmsEnabled() { return smsEnabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isWhatsappEnabled() { return whatsappEnabled; }
    public boolean isNotifyLandlord() { return notifyLandlord; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
