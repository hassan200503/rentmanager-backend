-- V33__fix_rent_ledger_timestamptz_and_external_reference_uniqueness.sql

ALTER TABLE rent_ledger_entries
ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

ALTER TABLE rent_transactions
ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- Replaces the plain index with a partial unique index: enforces the
-- M-Pesa replay guard at the DB level. RentTransaction has no status field
-- to distinguish "already processed" from "new" (unlike PaymentIntent), so
-- a DB constraint is the only real guard here.
DROP INDEX IF EXISTS idx_rent_transactions_external_reference;

CREATE UNIQUE INDEX uk_rent_transactions_tenant_external_reference
    ON rent_transactions (tenant_id, external_reference)
    WHERE external_reference IS NOT NULL;