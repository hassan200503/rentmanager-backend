-- V44__create_commission_policies.sql
--
-- Effective-dated commission policy table. A platform-wide default has
-- landlord_org_id IS NULL; per-landlord overrides have landlord_org_id set.
-- Changing a rate means inserting a new row + deactivating the old one —
-- never mutating a rate in place.

CREATE TABLE commission_policies (
    id                  UUID PRIMARY KEY,
    landlord_org_id     UUID,                               -- NULL = platform default
    rate_percent        NUMERIC(5, 2) NOT NULL,             -- e.g. 5.00 = 5%
    effective_from      TIMESTAMPTZ NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_by          VARCHAR(200) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_commission_policies_landlord
    ON commission_policies (landlord_org_id, active)
    WHERE active = TRUE;

CREATE INDEX idx_commission_policies_active
    ON commission_policies (active)
    WHERE active = TRUE;
