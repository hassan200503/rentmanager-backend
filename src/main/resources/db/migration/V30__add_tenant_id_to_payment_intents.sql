-- VXX__add_tenant_id_to_payment_intents.sql
-- Rename this file with the correct next Flyway version number before running.
--
-- Adds tenant scoping to payment_intents, backfilled transitively via the
-- owning unit (payment_intents has no direct tenant relationship today —
-- it's derived from unit_id -> units.tenant_id, the same source
-- UnitReservationTransactionService already resolves at creation time).
--
-- SAFETY — run this BEFORE applying the migration, via psql against real
-- data, to confirm the backfill will be complete:
--
--   SELECT count(*) FROM payment_intents pi
--   LEFT JOIN units u ON u.id = pi.unit_id
--   WHERE u.id IS NULL;
--
-- This must return 0. If it does not, do NOT proceed with the NOT NULL
-- step below — investigate the orphaned payment_intents rows first
-- (e.g. a unit that was hard-deleted after a payment_intent referenced it).

-- Step 1: add nullable column
ALTER TABLE payment_intents ADD COLUMN tenant_id UUID;

-- Step 2: backfill from the owning unit
UPDATE payment_intents pi
SET tenant_id = u.tenant_id
    FROM units u
WHERE u.id = pi.unit_id;

-- Step 3: enforce NOT NULL now that backfill is complete.
-- This will fail loudly (and correctly) if the pre-check above was skipped
-- and any row was left unbackfilled — do not weaken this to allow nulls.
ALTER TABLE payment_intents ALTER COLUMN tenant_id SET NOT NULL;

-- Step 4: index for tenant-scoped queries (landlord-facing PaymentIntent
-- views, once built, will filter by this)
CREATE INDEX idx_payment_intents_tenant_id ON payment_intents (tenant_id);