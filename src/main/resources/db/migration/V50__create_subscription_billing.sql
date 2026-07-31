-- V50__create_subscription_billing.sql
--
-- Phase 1: dual revenue model (COMMISSION vs PREMIUM_MONTHLY).
--
-- 1. Tenants gain landlord-level billing state. The billing mode is the
--    single source of truth for revenue treatment at rent-payment time:
--    COMMISSION deducts per-policy commission; PREMIUM_MONTHLY deducts
--    nothing (100% B2C to the landlord) in exchange for the flat monthly
--    fee collected by the subscription billing flow. Subscription period
--    state lives on the tenant row itself, honoring the scaffold's own
--    documented rule ("subscription data lives inside Tenant aggregate") --
--    the orphaned tenant_subscriptions row model was removed rather than
--    duplicating state across two aggregates.
-- 2. subscription_plans becomes the platform-global tier catalog (code
--    UNIQUE, no tenant_id) matching the existing domain SubscriptionPlan.
--    Seeded with the four confirmed tiers. ENTERPRISE (75+ units) is
--    deliberately NOT self-service: price NULL and self_service FALSE, so
--    the switch API rejects it and routes to manual contact.
-- 3. subscription_payment_requests records each M-Pesa STK push (initial
--    activation or renewal), mirroring rent_payment_requests (V39).

-- ---------------------------------------------------------------------
-- tenants: landlord billing mode + active premium plan state
-- ---------------------------------------------------------------------
ALTER TABLE tenants
    ADD COLUMN billing_mode        VARCHAR(50) NOT NULL DEFAULT 'COMMISSION',
    ADD COLUMN subscription_plan_id UUID,
    ADD COLUMN plan_start_date     DATE,
    ADD COLUMN plan_end_date       DATE,
    ADD COLUMN plan_grace_ends_at  DATE,
    ADD COLUMN plan_auto_renew     BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX idx_tenant_billing_mode ON tenants (billing_mode);
CREATE INDEX idx_tenant_plan_end_date ON tenants (plan_end_date);
CREATE INDEX idx_tenant_plan_grace_ends_at ON tenants (plan_grace_ends_at);

-- ---------------------------------------------------------------------
-- subscription_plans: platform-global tier catalog (self-serve tiers)
-- ---------------------------------------------------------------------
CREATE TABLE subscription_plans (
    id             UUID PRIMARY KEY,
    version        BIGINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    code           VARCHAR(50)  NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    billing_cycle  VARCHAR(50)  NOT NULL,
    max_units      INT,
    monthly_price  NUMERIC(19, 2),
    yearly_price   NUMERIC(19, 2),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    self_service   BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT uq_subscription_plans_code UNIQUE (code)
);

CREATE INDEX idx_subscription_plans_active ON subscription_plans (active);
CREATE INDEX idx_subscription_plans_self_service ON subscription_plans (self_service);

-- Seeded catalog (Phase 1 confirmations):
--   Starter    <= 10 units  KES 2,500/month
--   Growth     <= 30 units  KES 5,500/month
--   Portfolio  <= 75 units  KES 9,500/month
--   Enterprise  75+ units   custom pricing, manual contact (not self-serve)
INSERT INTO subscription_plans
    (id, version, created_at, updated_at, code, name, description,
     billing_cycle, max_units, monthly_price, yearly_price, active, self_service)
VALUES
    ('10000000-0000-4000-8000-000000000001', 0, now(), now(),
     'STARTER', 'Starter', 'Up to 10 units', 'MONTHLY', 10, 2500.00, NULL, TRUE, TRUE),
    ('10000000-0000-4000-8000-000000000002', 0, now(), now(),
     'GROWTH', 'Growth', '11 to 30 units', 'MONTHLY', 30, 5500.00, NULL, TRUE, TRUE),
    ('10000000-0000-4000-8000-000000000003', 0, now(), now(),
     'PORTFOLIO', 'Portfolio', '31 to 75 units', 'MONTHLY', 75, 9500.00, NULL, TRUE, TRUE),
    ('10000000-0000-4000-8000-000000000004', 0, now(), now(),
     'ENTERPRISE', 'Enterprise', '75+ units - custom pricing, contact sales', 'MONTHLY',
     NULL, NULL, NULL, TRUE, FALSE);

-- ---------------------------------------------------------------------
-- subscription_payment_requests: STK push tracking (activation + renewal)
-- ---------------------------------------------------------------------
CREATE TABLE subscription_payment_requests (
    id                         UUID PRIMARY KEY,
    tenant_id                  UUID NOT NULL REFERENCES tenants (id),
    subscription_plan_id       UUID NOT NULL REFERENCES subscription_plans (id),
    amount                     NUMERIC(19, 2) NOT NULL,
    mpesa_phone                VARCHAR(20) NOT NULL,
    purpose                    VARCHAR(30) NOT NULL,
    status                     VARCHAR(30) NOT NULL,
    mpesa_checkout_request_id  VARCHAR(255),
    mpesa_receipt_number       VARCHAR(50),
    failure_reason             VARCHAR(255),
    version                    BIGINT NOT NULL DEFAULT 0,
    created_at                 TIMESTAMPTZ NOT NULL,
    updated_at                 TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_sub_payment_requests_tenant_id ON subscription_payment_requests (tenant_id);
CREATE INDEX idx_sub_payment_requests_status ON subscription_payment_requests (status);
CREATE INDEX idx_sub_payment_requests_created_at ON subscription_payment_requests (created_at);

-- NULL-safe: checkout id isn't known until the STK push call returns, same
-- pattern as V39's uk_rent_payment_requests_checkout_request_id.
CREATE UNIQUE INDEX uk_sub_payment_requests_checkout_request_id
    ON subscription_payment_requests (mpesa_checkout_request_id)
    WHERE mpesa_checkout_request_id IS NOT NULL;
