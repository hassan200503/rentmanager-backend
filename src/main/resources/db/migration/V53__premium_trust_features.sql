-- V53__premium_trust_features.sql
--
-- Phases 2b/4a/4b/5 of the premium build:
--
-- 1. tenants: landlord emergency contact (Phase 2b) - rendered on the
--    renter portal only when actually set.
-- 2. maintenance_requests: first_landlord_response_at (Phase 4a/5) - the
--    exact field the response-time SLA badge needs. Captured on the first
--    landlord status mutation, never overwritten afterwards.
-- 3. landlord_reviews (Phase 4b): verified renter-only reviews. A review
--    is only valid from a renter who has or had an active lease with the
--    landlord. One review per renter per landlord (unique on
--    tenant_id + tenant_profile_id).
-- 4. notification_deliveries (Phase 5): retryable outbox for the
--    maintenance-request fan-out (SMS / email / WhatsApp). Each channel
--    is an independent row so one channel failing can never block the
--    others or the request write itself. The dispatch scheduler retries
--    FAILED rows with backoff and never re-sends a SENT row.

-- ---------------------------------------------------------------------
-- 1. Emergency contact (Phase 2b)
-- ---------------------------------------------------------------------
ALTER TABLE tenants
    ADD COLUMN emergency_contact_phone VARCHAR(20),
    ADD COLUMN emergency_contact_24h BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------------------------------------------------------------------
-- 2. Maintenance response SLA (Phase 4a/5)
-- ---------------------------------------------------------------------
ALTER TABLE maintenance_requests
    ADD COLUMN first_landlord_response_at TIMESTAMP;

CREATE INDEX idx_maintenance_requests_priority ON maintenance_requests (priority);
CREATE INDEX idx_maintenance_requests_created_at ON maintenance_requests (created_at);

-- ---------------------------------------------------------------------
-- 3. Landlord reviews (Phase 4b)
-- ---------------------------------------------------------------------
CREATE TABLE landlord_reviews (
    id                UUID         NOT NULL,
    tenant_id         UUID         NOT NULL,
    tenant_profile_id UUID         NOT NULL,
    lease_id          UUID         NOT NULL,
    rating            SMALLINT     NOT NULL,
    comment           VARCHAR(1000),
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_landlord_reviews PRIMARY KEY (id),
    CONSTRAINT fk_landlord_reviews_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_landlord_reviews_tenant_profile
        FOREIGN KEY (tenant_profile_id) REFERENCES tenant_profile (id),
    CONSTRAINT fk_landlord_reviews_lease
        FOREIGN KEY (lease_id) REFERENCES leases (id),
    CONSTRAINT ck_landlord_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_landlord_reviews_renter UNIQUE (tenant_id, tenant_profile_id)
);

CREATE INDEX idx_landlord_reviews_tenant_id ON landlord_reviews (tenant_id);

-- ---------------------------------------------------------------------
-- 4. Notification outbox (Phase 5)
-- ---------------------------------------------------------------------
CREATE TABLE notification_deliveries (
    id               UUID         NOT NULL,
    tenant_id        UUID         NOT NULL,
    event_id         UUID         NOT NULL,
    channel          VARCHAR(30)  NOT NULL,
    recipient        VARCHAR(255) NOT NULL,
    subject          VARCHAR(255),
    message          TEXT         NOT NULL,
    metadata         TEXT,
    status           VARCHAR(30)  NOT NULL,
    attempt_count    INT          NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL,
    last_error       VARCHAR(500),
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,

    CONSTRAINT pk_notification_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_notification_deliveries_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE INDEX idx_notification_deliveries_due
    ON notification_deliveries (status, next_attempt_at);
CREATE INDEX idx_notification_deliveries_tenant
    ON notification_deliveries (tenant_id);
