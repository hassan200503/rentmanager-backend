-- Add owner-configurable trial duration to the platform settings singleton.
-- Default 30 days (one rent cycle) is a production-appropriate value for
-- the Kenyan landlord market. Existing rows get the default automatically.
ALTER TABLE platform_settings
    ADD COLUMN trial_duration_days INT NOT NULL DEFAULT 30;
