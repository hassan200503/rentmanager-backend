-- V64__create_platform_settings.sql
--
-- Platform-wide owner configuration, stored as a single singleton row so
-- the platform owner can change operational knobs at runtime without a
-- redeploy. Every field maps to a real behaviour or canonical identifier:
--
--   premium_grace_days                 - how long a premium landlord keeps
--                                        premium benefits after a failed
--                                        renewal before auto-revert to
--                                        COMMISSION (SubscriptionExpirySweeper).
--   subscription_payment_expiry_minutes - stale STK subscription pushes are
--                                        swept EXPIRED after this window.
--   disbursement_max_retry_attempts    - per-disbursement B2C retry budget
--                                        before a payout is flagged for
--                                        manual attention.
--   revenue_*                          - the platform's canonical M-Pesa
--                                        collection/payout identifiers shown
--                                        to operators (business number,
--                                        Paybill, Till, B2C shortcode, ops
--                                        phone). Non-secret values only;
--                                        Daraja secret credentials stay in
--                                        env configuration.
--   support_email / support_phone      - platform support contact surfaced
--                                        across operator console.
--
-- Singleton constraint guarantees exactly one owner-configurable row.

CREATE TABLE platform_settings (
    id         UUID PRIMARY KEY,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    updated_by VARCHAR(200) NOT NULL,

    premium_grace_days                   INT  NOT NULL,
    subscription_payment_expiry_minutes INT  NOT NULL,
    disbursement_max_retry_attempts     INT  NOT NULL,

    revenue_business_shortcode VARCHAR(20),
    revenue_paybill            VARCHAR(20),
    revenue_till               VARCHAR(20),
    revenue_b2c_shortcode      VARCHAR(20),
    revenue_mpesa_phone        VARCHAR(20),

    support_email VARCHAR(150),
    support_phone VARCHAR(20),

    CONSTRAINT uk_platform_settings_singleton
        CHECK (id = '00000000-0000-4000-8000-000000000001')
);

-- Seed the singleton with the current platform defaults so the scheduler
-- and the admin settings page always have a row to read.
INSERT INTO platform_settings
    (id, version, created_at, updated_at, updated_by,
     premium_grace_days, subscription_payment_expiry_minutes,
     disbursement_max_retry_attempts)
VALUES
    ('00000000-0000-4000-8000-000000000001', 0, now(), now(), 'system-seed',
     7, 30, 3);