-- V40__create_unmatched_payments.sql
-- Stores M-Pesa callback payloads that could not be matched to an existing
-- RentPaymentRequest. Admin reviews these via the manual review queue UI.

CREATE TABLE unmatched_payments (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    transaction_id      VARCHAR(100) NOT NULL,
    amount              NUMERIC(19, 2) NOT NULL,
    phone_number        VARCHAR(50) NOT NULL,
    account_reference   VARCHAR(255),
    occurred_at         TIMESTAMP NOT NULL,
    mpesa_checkout_request_id VARCHAR(255),
    mpesa_receipt_number      VARCHAR(50),
    result_code         INTEGER,
    result_desc         VARCHAR(500),
    resolved            BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at         TIMESTAMP,
    resolved_by         VARCHAR(255),
    resolved_unit_id    UUID,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_unmatched_payments_tenant_id ON unmatched_payments (tenant_id);
CREATE INDEX idx_unmatched_payments_tenant_resolved ON unmatched_payments (tenant_id, resolved);
