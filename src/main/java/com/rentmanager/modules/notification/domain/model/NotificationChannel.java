package com.rentmanager.modules.notification.domain.model;

public enum NotificationChannel {
    SMS,
    EMAIL,
    WHATSAPP,
    /** Mobile push. Recipient is a device token; metadata carries owner and deep-link data. */
    PUSH
}
