-- =========================================================
-- Deposits table
-- =========================================================
CREATE TABLE deposits (
                          id                  UUID PRIMARY KEY,
                          tenant_id           UUID NOT NULL,
                          lease_id            UUID NOT NULL,
                          unit_id             UUID NOT NULL,
                          tenant_profile_id   UUID NOT NULL,
                          amount_required     NUMERIC(19,2) NOT NULL,
                          amount_paid         NUMERIC(19,2) NOT NULL DEFAULT 0,
                          amount_refunded     NUMERIC(19,2) NOT NULL DEFAULT 0,
                          status              VARCHAR(50) NOT NULL,
                          paid_at             TIMESTAMP,
                          refunded_at         TIMESTAMP,
                          created_at          TIMESTAMP NOT NULL DEFAULT now(),
                          updated_at          TIMESTAMP,
                          version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_deposit_tenant ON deposits (tenant_id);
CREATE INDEX idx_deposit_lease ON deposits (lease_id);
CREATE INDEX idx_deposit_status ON deposits (status);

-- =========================================================
-- Commission rate on tenants
-- =========================================================
ALTER TABLE tenants
    ADD COLUMN commission_rate NUMERIC(5,4) NOT NULL DEFAULT 0.0500;