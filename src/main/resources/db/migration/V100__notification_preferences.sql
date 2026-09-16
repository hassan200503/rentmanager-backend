-- V100: Per-person push notification preferences.
--
-- Keyed like push_devices by Clerk user id: preferences belong to a person,
-- not to a landlord organisation. Absence of a row means the default (on).
-- These govern PUSH only. SMS, email and WhatsApp keep their existing rules:
-- payment confirmations and rent reminders are part of how a landlord
-- communicates with a renter, and switching them off is not a person-level
-- app setting.

CREATE TABLE notification_preferences (
    clerk_user_id  VARCHAR(255) NOT NULL,
    category       VARCHAR(32)  NOT NULL,
    push_enabled   BOOLEAN      NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_notification_preferences PRIMARY KEY (clerk_user_id, category),
    CONSTRAINT ck_notification_preferences_category CHECK (category IN ('RENT_PAYMENTS', 'MAINTENANCE'))
);
