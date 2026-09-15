-- V97: Push notification device registry (mobile app).
--
-- One row per physical app installation's Expo push token. Keyed by the
-- Clerk user id rather than users.id or tenant_profile.id because the same
-- person can be a landlord-side user AND a renter under several landlords;
-- every one of those identities shares one Clerk user id, and a push is sent
-- to a person, not to a tenancy.
--
-- NOT tenant-scoped on purpose: the device belongs to the person. Tenant
-- isolation is enforced where a delivery is created — a listener only ever
-- resolves recipients from the landlord/lease the event belongs to.
--
-- A token is unique across the table. When a second person signs in on the
-- same phone, registration moves the row to them (clerk_user_id is updated)
-- so the previous person's notifications stop reaching that device. The
-- dispatcher re-checks ownership at send time as well, so a delivery queued
-- for the previous owner is dropped rather than shown to the new one.

CREATE TABLE push_devices (
    id              UUID         NOT NULL,
    clerk_user_id   VARCHAR(255) NOT NULL,
    push_token      VARCHAR(255) NOT NULL,
    platform        VARCHAR(16)  NOT NULL,
    app_version     VARCHAR(64),
    revoked_at      TIMESTAMPTZ,
    last_seen_at    TIMESTAMPTZ  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_push_devices PRIMARY KEY (id),
    CONSTRAINT uq_push_devices_token UNIQUE (push_token),
    CONSTRAINT ck_push_devices_platform CHECK (platform IN ('IOS', 'ANDROID'))
);

CREATE INDEX idx_push_devices_active_user
    ON push_devices (clerk_user_id)
    WHERE revoked_at IS NULL;
