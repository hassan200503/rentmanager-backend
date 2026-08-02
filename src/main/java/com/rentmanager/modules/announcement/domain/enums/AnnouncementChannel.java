package com.rentmanager.modules.announcement.domain.enums;

import com.rentmanager.modules.notification.domain.model.NotificationChannel;

/**
 * Delivery channels an announcement can be broadcast on. IN_APP is always
 * on - the landlord cannot deselect it. SMS/EMAIL/WHATSAPP are paid
 * channels the landlord can deselect per send.
 */
public enum AnnouncementChannel {

    IN_APP,
    SMS,
    EMAIL,
    WHATSAPP;

    /**
     * Maps to the shared notification outbox channel for external
     * dispatch. IN_APP has no outbox counterpart - it is never
     * dispatched externally.
     */
    public NotificationChannel toNotificationChannel() {
        return switch (this) {
            case SMS -> NotificationChannel.SMS;
            case EMAIL -> NotificationChannel.EMAIL;
            case WHATSAPP -> NotificationChannel.WHATSAPP;
            case IN_APP -> throw new IllegalStateException("IN_APP has no external notification channel");
        };
    }
}
