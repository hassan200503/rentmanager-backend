
ALTER TABLE leases
    ADD COLUMN late_fee_amount NUMERIC(19,2),
    ADD COLUMN grace_period_days INTEGER,
    ADD COLUMN auto_renew BOOLEAN NOT NULL DEFAULT FALSE;