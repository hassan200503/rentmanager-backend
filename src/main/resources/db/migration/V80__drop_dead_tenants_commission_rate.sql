-- V80__drop_dead_tenants_commission_rate.sql
--
-- tenants.commission_rate (V20, scale 4, e.g. 0.0500) was never the live
-- commission source and nothing reads it — the actual rate for M-Pesa
-- callback processing comes from commission_policies.rate_percent (scale 2)
-- via CommissionPolicyService.getActiveRate(). Two columns, two scales, one
-- truth; drop the dead one so nobody wires an admin screen to it.

ALTER TABLE tenants
    DROP COLUMN commission_rate;
