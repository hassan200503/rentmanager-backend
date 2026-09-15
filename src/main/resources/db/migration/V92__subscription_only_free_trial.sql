-- V92: Free-trial window tracking for the subscription-only revenue model.
--
-- The pre-subscription state (billing_mode = 'COMMISSION') was never a real
-- commission-collection arrangement for this platform: all landlords use
-- DIRECT collection mode, so commission_policies rates never reach a rent
-- payment callback. Surfacing that internal state as "commission billing"
-- to landlords was misleading. This migration adds the column that lets us
-- show a proper 90-day free-trial countdown instead.
--
-- Existing tenants in TRIAL status are back-filled: they receive 90 days
-- from their account-creation timestamp, which matches the constructor
-- behaviour introduced in the same commit.

ALTER TABLE tenants
    ADD COLUMN IF NOT EXISTS free_trial_ends_at TIMESTAMPTZ;

-- Back-fill existing TRIAL / COMMISSION tenants (those who have not yet
-- activated a premium subscription). New tenants will have the field set
-- by the constructor; this covers anyone created before this migration.
UPDATE tenants
SET free_trial_ends_at = (created_at + INTERVAL '90 days')
WHERE subscription_status = 'TRIAL'
  AND billing_mode = 'COMMISSION'
  AND free_trial_ends_at IS NULL;

-- Partial index: only TRIAL-status rows are ever queried by the scheduler
-- and the status API when checking trial expiry.
CREATE INDEX idx_tenants_free_trial_ends_at
    ON tenants (free_trial_ends_at)
    WHERE subscription_status = 'TRIAL';
