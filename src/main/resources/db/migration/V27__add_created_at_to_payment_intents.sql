-- No-op: created_at column already existed on payment_intents from V24 (original table creation).
-- This migration originally assumed the column was missing; verified via psql on 2026-07-05 that it
-- was already present (timestamptz, not null, default now()) as part of the base entity columns.
-- Kept as a no-op rather than deleted to preserve Flyway's version sequence.
SELECT 1;
