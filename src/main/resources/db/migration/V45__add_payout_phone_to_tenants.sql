-- V45__add_payout_phone_to_tenants.sql
--
-- B2C disbursement target: the M-Pesa phone number where the landlord's net
-- rent share (after platform commission) is sent. Nullable — only required
-- when the landlord opts into automatic rent-payment disbursement.

ALTER TABLE tenants
    ADD COLUMN payout_phone_number VARCHAR(20);
