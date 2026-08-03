-- V58__create_mri_rate_schedule.sql
--
-- Landlord Monthly Rental Income (MRI) rate schedule.
--
-- MRI is the withholding-tax regime on residential rents. The effective
-- constructor is the Finance Act; the rate has changed over time and is
-- expected to change again, so it is modelled as a dated schedule rather
-- than a constant.
--
--   * Only the single row with status = 'ACTIVE' (the latest effective_from)
--     is ever read for computation. Everything else is parked for reference
--     or pre-staged SCHEDULED rows that a human must verify before Activating.
--   * rate_percent is a decimal fraction with 4dp (0.075 = 7.5%).
--   * superseding rows must not overlap: effective_from of a row is exclusive
--     with the previous row's effective_to when it is backfilled. For
--     end-to-end rows effective_to is NULL (open-ended).

CREATE TABLE mri_rate_schedule (
    id               UUID PRIMARY KEY,
    effective_from   DATE           NOT NULL,
    effective_to     DATE,
    rate_percent     NUMERIC(5,4)   NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    source_reference VARCHAR(255),
    version          BIGINT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ    NOT NULL,

    CONSTRAINT ck_mri_rate_schedule_status
        CHECK (status IN ('ACTIVE', 'SCHEDULED', 'SUPERSEDED')),
    CONSTRAINT ck_mri_rate_schedule_valid_period
        CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_mri_rate_schedule_rate_positive
        CHECK (rate_percent > 0)
);

CREATE INDEX idx_mri_rate_schedule_effective
    ON mri_rate_schedule (effective_from);

-- Seed: the Finance Act 2023 cut the residential MRI rate from 10% to 7.5%,
-- effective 1 January 2024. This is the regime in force for the rest of the
-- historical book.
INSERT INTO mri_rate_schedule (
    id, effective_from, effective_to, rate_percent, status,
    source_reference, version, created_at, updated_at
) VALUES (
    '01000000-0000-4000-8000-000000000001',
    DATE '2024-01-01', NULL, 0.0750, 'ACTIVE',
    'Finance Act 2023 (LR. 21, No. 7 of 2023)', 0, NOW(), NOW()
);

-- Finance Act 2026 proposed reverting MRI to 10% from 1 July 2026 for
-- landlords under disbursement of rental income. Whether this survived into
-- the final signed Act is UNCONFIRMED: post-signing sources conflict, and
-- this area is flagged for a tax-advisor verification (brief item A1)
-- BEFORE the live path is enabled.
--
-- This row ships SCHEDULED (inactive). It is never used in computation until
-- a human verifies the gazetted text against KRA guidance and flips it ACTIVE
-- via the rate-policy service.
INSERT INTO mri_rate_schedule (
    id, effective_from, effective_to, rate_percent, status,
    source_reference, version, created_at, updated_at
) VALUES (
    '01000000-0000-4000-8000-000000000002',
    DATE '2026-07-01', NULL, 0.1000, 'SCHEDULED',
    'Finance Act 2026 (UNVERIFIED — advisor sign-off required)', 0, NOW(), NOW()
);