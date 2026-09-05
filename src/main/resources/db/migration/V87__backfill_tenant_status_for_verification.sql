-- V87__backfill_tenant_status_for_verification.sql
--
-- "Verified landlord" was displayed on two surfaces, computed two different
-- ways, and neither one meant what the badge claimed.
--
--   * The renter portal treated subscription status IN (ACTIVE, TRIAL,
--     GRACE_PERIOD) as verification, so a five-minute-old free trial
--     displayed as a verified landlord.
--
--   * Public listings read tenants.verified — a column V42 backfilled to
--     true for everybody, defaulted false since, and which NOTHING in the
--     codebase has ever written. Organization.verify() maps to the
--     organizations table, not this one, and is never called.
--
-- The live data showed both at once: a tenant row reading verified = true on
-- listings and unverified in the portal at the same moment.
--
-- The definition the product actually wants is "finished onboarding and been
-- approved", and Tenant.status already models exactly that: it starts at
-- PENDING and only reaches ACTIVE through a deliberate activate(). Both
-- surfaces now read it.
--
-- BACKFILL POLICY — the judgement call in this migration.
-- Existing rows have status NULL because the column pre-dates the state
-- machine. Two options, both defensible:
--
--   Grandfather everyone to ACTIVE : nobody loses a badge they had, but
--                                    accounts nobody ever approved keep one.
--   Set everyone to PENDING        : honest, and it silently strips the badge
--                                    from live landlords with paying tenants.
--
-- Neither alone is right, so this splits on evidence: a landlord with at
-- least one lease that has actually been activated has demonstrably been
-- through onboarding and is collecting rent through the platform. Everyone
-- else starts at PENDING and can be approved deliberately.
--
-- That is a conservative reading. It may set PENDING on a landlord who is
-- legitimately set up but has not signed a lease yet; the cost of that is a
-- missing badge, which is recoverable. The cost of the opposite error is
-- vouching for an account nobody checked.

UPDATE tenants t
SET status = 'ACTIVE'
WHERE t.status IS NULL
  AND EXISTS (
      SELECT 1
      FROM leases l
      WHERE l.tenant_id = t.id
        AND l.status IN ('ACTIVE', 'RENEWED', 'EXPIRED', 'TERMINATED')
  );

UPDATE tenants
SET status = 'PENDING'
WHERE status IS NULL;

-- Retire the dead column. It has never been written by application code, and
-- leaving it in place guarantees somebody eventually wires an admin screen to
-- it and reintroduces exactly the split-brain this migration closes.
--
-- Dropped rather than deprecated-in-place for that reason: a column that
-- still exists is a column somebody will use.
ALTER TABLE tenants DROP COLUMN IF EXISTS verified;
