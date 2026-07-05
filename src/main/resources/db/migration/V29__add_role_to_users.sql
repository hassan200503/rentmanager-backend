ALTER TABLE users ADD COLUMN role VARCHAR(20);

-- Backfill: every user currently linked to a tenant predates the role
-- concept entirely, so there is no historical signal to distinguish
-- OWNER from MANAGER/STAFF among them. Defaulting all of them to OWNER
-- is only correct if each tenant currently has exactly one linked user.
-- VERIFY THIS ASSUMPTION (see caveat above) before running in any
-- environment with real data.
UPDATE users
SET role = 'OWNER'
WHERE tenant_id IS NOT NULL
  AND role IS NULL;

-- Users with no tenant_id (renters, or pending-onboarding identities)
-- correctly remain role = NULL — role only has meaning for landlord-org
-- members.