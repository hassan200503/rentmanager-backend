-- V55__create_announcements.sql
--
-- Landlord Announcements & Broadcast Messaging:
--
-- 1. tenant_profile.whatsapp_opt_in: explicit renter consent for WhatsApp
--    broadcasts. Meta Business Policy requires opt-in before any
--    business-initiated template message; this column is the consent
--    record, captured via a real checkbox in the renter portal. Defaults
--    to FALSE - consent is never assumed.
-- 2. announcements: one row per broadcast. Holds the landlord's full
--    message text, INFO/URGENT priority (mirrors the maintenance-request
--    badge language), the selected channels, and an optional expiry.
--    channels is a comma-separated list (IN_APP,SMS,EMAIL,WHATSAPP);
--    IN_APP is always included. Tenant-scoped - the write always
--    succeeds regardless of what happens downstream in the fan-out.
-- 3. announcement_deliveries: one row per active renter per selected
--    channel - the execution record that powers the landlord-side
--    delivery stats and the renter-side read tracking (read_at). Rows
--    are PENDING until dispatched by the announcement sweep; WhatsApp
--    rows for renters without opt-in are created as SKIPPED_NO_OPTIN
--    (never attempted, never retried). IN_APP rows are DELIVERED at
--    creation - the announcement is visible in the portal immediately.
--    The sweep retries FAILED rows with backoff and never re-sends a
--    SENT/DELIVERED row.

-- ---------------------------------------------------------------------
-- 1. Renter WhatsApp opt-in
-- ---------------------------------------------------------------------
ALTER TABLE tenant_profile
    ADD COLUMN whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------
-- 2. Announcements
-- ---------------------------------------------------------------------
CREATE TABLE announcements (
    id           UUID         NOT NULL,
    tenant_id    UUID         NOT NULL,
    author_id    UUID         NOT NULL,
    message      TEXT         NOT NULL,
    priority     VARCHAR(30)  NOT NULL,
    channels     VARCHAR(100) NOT NULL,
    expires_at   TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_announcements PRIMARY KEY (id),
    CONSTRAINT fk_announcements_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE INDEX idx_announcements_tenant_created
    ON announcements (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------
-- 3. Announcement deliveries (fan-out execution + read tracking)
-- ---------------------------------------------------------------------
CREATE TABLE announcement_deliveries (
    id                UUID         NOT NULL,
    tenant_id         UUID         NOT NULL,
    announcement_id   UUID         NOT NULL,
    renter_profile_id UUID         NOT NULL,
    channel           VARCHAR(30)  NOT NULL,
    status            VARCHAR(30)  NOT NULL,
    sent_at           TIMESTAMPTZ,
    read_at           TIMESTAMPTZ,
    attempt_count     INT          NOT NULL DEFAULT 0,
    next_attempt_at   TIMESTAMPTZ,
    last_error        VARCHAR(500),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_announcement_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_announcement_deliveries_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_announcement_deliveries_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements (id),
    CONSTRAINT fk_announcement_deliveries_renter
        FOREIGN KEY (renter_profile_id) REFERENCES tenant_profile (id)
);

CREATE INDEX idx_announcement_deliveries_due
    ON announcement_deliveries (status, next_attempt_at);
CREATE INDEX idx_announcement_deliveries_announcement
    ON announcement_deliveries (tenant_id, announcement_id);
CREATE INDEX idx_announcement_deliveries_renter
    ON announcement_deliveries (tenant_id, renter_profile_id, channel);
