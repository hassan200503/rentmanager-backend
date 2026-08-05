-- V63: Make notification_deliveries.next_attempt_at nullable
-- SENT deliveries don't need a retry timestamp, so the domain model sets
-- nextAttemptAt = null. The original NOT NULL constraint causes a violation.

ALTER TABLE notification_deliveries
    ALTER COLUMN next_attempt_at DROP NOT NULL;
