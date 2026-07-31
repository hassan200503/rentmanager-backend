-- V51__create_subscription_standing_orders.sql
--
-- Phase 1 v2: M-Pesa Ratiba (standing-order) autobilling.
--
-- Ratiba replaces the per-cycle STK-push renewal sweep: the landlord
-- pre-authorizes a monthly standing order into our Paybill with their
-- tenant code as the account reference; each execution lands in the C2B
-- confirmation callback and extends the paid period by one billing cycle.
-- The scheduler no longer initiates payments at all -- it only advances
-- state (period ended unpaid -> GRACE_PERIOD -> LAPSED/COMMISSION revert).
--
-- 1. subscription_standing_orders tracks merchant-initiated Ratiba order
--    creations (Daraja createStandingOrderExternal): PENDING_AUTHORIZATION
--    after the API accepts the request (the landlord still has to consent
--    via the NI push), ACTIVE/FAILED once the async callback arrives.
-- 2. unmatched_payments.tenant_id is made nullable: a C2B payment with an
--    unrecognized account reference has no resolvable tenant, and it must
--    still be captured fail-closed for manual reconciliation instead of
--    being silently dropped or applied.

CREATE TABLE subscription_standing_orders (
    id                        UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL REFERENCES tenants (id),
    account_reference         VARCHAR(12) NOT NULL,
    amount                    NUMERIC(19, 2) NOT NULL,
    frequency                 VARCHAR(20) NOT NULL,
    status                    VARCHAR(30) NOT NULL,
    ratiba_response_ref_id    VARCHAR(255),
    ratiba_transaction_id     VARCHAR(50),
    start_date                DATE,
    end_date                  DATE,
    failure_reason            VARCHAR(255),
    version                   BIGINT NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_standing_orders_tenant_id ON subscription_standing_orders (tenant_id);
CREATE INDEX idx_standing_orders_status ON subscription_standing_orders (status);
CREATE INDEX idx_standing_orders_response_ref_id ON subscription_standing_orders (ratiba_response_ref_id);

ALTER TABLE unmatched_payments ALTER COLUMN tenant_id DROP NOT NULL;
