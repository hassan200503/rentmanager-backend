-- V61__create_monthly_rental_income_filings.sql
--
-- eRITS requires landlords to remit monthly; our platform computes the
-- monthly Residential MRI position per landlord (gross rental income,
-- applicable rate, tax due) and records one row per (landlord, month).
--
--   * period: first day of the filing month (e.g. 2026-08-01 for August).
--   * gross_rental_income: sum of RESIDENTIAL rent payments recorded in that
--     month (type = PAYMENT on the rent ledger). A landlord with no payments
--     in the month gets a NIL return (is_nil_return TRUE, amounts zero) —
--     eRITS accepts a nil return in lieu of a zero-value filing.
--   * mri_rate_applied: the ACTIVE MRI rate for the filing month from
--     mri_rate_schedule (never a SCHEDULED/SUPERSEDED row).
--   * mri_tax_due = gross_rental_income * mri_rate_applied.
--   * status: COMPUTED -> READY_FOR_MANUAL | TRANSMITTED | FAILED.
--     The landlord previews/approves the computed figure (READY_FOR_MANUAL
--     when the landlord opts to file manually), then the eRITS transmission
--     (Phase 2/3) marks it TRANSMITTED/FAILED with retry bookkeeping.
--
-- Note: whether KRA applies MRI per-monthly-lump or per-property splits, and
-- whether any landlord gets annualised (the 2026 proposals), is open until
-- KRA's guidance is confirmed (brief item A1); the monthly landlord-level
-- computation is the conservative Phase-1 shape and can be narrowed later
-- without schema change.

CREATE TABLE monthly_rental_income_filings (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL REFERENCES tenants (id),
    period                DATE NOT NULL,
    gross_rental_income   NUMERIC(19,2) NOT NULL,
    is_nil_return         BOOLEAN NOT NULL,
    mri_rate_applied      NUMERIC(5,4) NOT NULL,
    mri_tax_due           NUMERIC(19,2) NOT NULL,
    status                VARCHAR(30) NOT NULL,
    computed_at           TIMESTAMPTZ NOT NULL,
    filed_at              TIMESTAMPTZ,
    transmitted_at        TIMESTAMPTZ,
    attempt_count         INT NOT NULL DEFAULT 0,
    next_attempt_at       TIMESTAMPTZ,
    last_error            VARCHAR(500),
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_monthly_filing_per_period
        UNIQUE (tenant_id, period),
    CONSTRAINT ck_monthly_filing_status
        CHECK (status IN ('COMPUTED', 'READY_FOR_MANUAL', 'TRANSMITTED', 'FAILED')),
    CONSTRAINT ck_monthly_filing_rate_positive
        CHECK (mri_rate_applied > 0),
    CONSTRAINT ck_monthly_filing_nil_consistency
        CHECK ((is_nil_return AND gross_rental_income = 0 AND mri_tax_due = 0)
               OR (NOT is_nil_return AND gross_rental_income >= 0 AND mri_tax_due >= 0))
);

CREATE INDEX idx_monthly_filings_due
    ON monthly_rental_income_filings (status, attempt_count, next_attempt_at);
CREATE INDEX idx_monthly_filings_tenant_period
    ON monthly_rental_income_filings (tenant_id, period);