-- Renter-facing "why did auto-pay fail" trust feature: a short, sanitized,
-- human-readable explanation of the most recent failed attempt. Cleared on
-- the next successful run. Deliberately never stores raw exception/provider
-- text (see AutoPaySettings.recordFailure javadoc) -- always one of a small
-- fixed set of safe strings written by AutoPayService.
ALTER TABLE auto_pay_settings
    ADD COLUMN last_failure_reason VARCHAR(255);
