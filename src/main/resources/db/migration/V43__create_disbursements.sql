-- V43__create_disbursements.sql

CREATE TABLE disbursements (
    id                              UUID PRIMARY KEY,
    tenant_id                       UUID NOT NULL,
    lease_id                        UUID,
    ledger_entry_id                 UUID,
    amount                          NUMERIC(19, 2) NOT NULL,
    recipient_phone                 VARCHAR(20) NOT NULL,
    recipient_name                  VARCHAR(200),
    command_id                      VARCHAR(50) NOT NULL,
    status                          VARCHAR(20) NOT NULL,
    mpesa_transaction_id            VARCHAR(100),
    mpesa_conversation_id           VARCHAR(100),
    mpesa_originator_conversation_id VARCHAR(100),
    failure_reason                  VARCHAR(500),
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_disbursements_tenant_id ON disbursements (tenant_id);
CREATE INDEX idx_disbursements_lease_id ON disbursements (lease_id);
CREATE INDEX idx_disbursements_ledger_entry_id ON disbursements (ledger_entry_id);

-- Partial unique index: originator_conversation_id is null until the Daraja
-- B2C call completes, so enforce uniqueness only when set.
CREATE UNIQUE INDEX uk_disbursements_originator_conversation_id
    ON disbursements (mpesa_originator_conversation_id)
    WHERE mpesa_originator_conversation_id IS NOT NULL;