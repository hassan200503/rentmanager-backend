-- V95: Backfill landlords whose subscription_status is NULL.
--
-- subscription_status was created nullable in V4 with no default. Before the
-- Tenant constructor started writing TRIAL on every new account, rows could be
-- persisted with a NULL status. V92 only back-filled rows WHERE
-- subscription_status = 'TRIAL', so NULL-status rows were skipped entirely and
-- received no free_trial_ends_at either.
--
-- Fix: treat every COMMISSION landlord with a NULL status as a TRIAL account.
-- Their trial window is anchored to their original created_at so the date is
-- historically correct — accounts older than 30 days will land in "trial
-- expired" state, which the UI handles gracefully with a "subscribe now" CTA.
-- Accounts created within the last 30 days will show as active trial.

UPDATE tenants
SET subscription_status = 'TRIAL',
    free_trial_ends_at  = COALESCE(free_trial_ends_at, created_at + INTERVAL '30 days')
WHERE billing_mode      = 'COMMISSION'
  AND subscription_status IS NULL
  AND subscription_plan_id IS NULL;
