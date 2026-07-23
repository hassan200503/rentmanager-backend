-- V39__create_rent_payment_requests.sql

CREATE TABLE rent_payment_requests (
                                       id                          UUID PRIMARY KEY,
                                       tenant_id                   UUID NOT NULL,
                                       lease_id                    UUID NOT NULL,
                                       rent_ledger_entry_id        UUID NOT NULL REFERENCES rent_ledger_entries (id),
                                       amount                      NUMERIC(19, 2) NOT NULL,
                                       mpesa_checkout_request_id   VARCHAR(255),
                                       status                      VARCHAR(20) NOT NULL,
                                       mpesa_receipt_number        VARCHAR(50),
                                       version                     BIGINT NOT NULL DEFAULT 0,
                                       created_at                  TIMESTAMPTZ NOT NULL,
                                       updated_at                  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_rent_payment_requests_tenant_id ON rent_payment_requests (tenant_id);
CREATE INDEX idx_rent_payment_requests_lease_id ON rent_payment_requests (lease_id);
CREATE INDEX idx_rent_payment_requests_rent_ledger_entry_id ON rent_payment_requests (rent_ledger_entry_id);

-- NULL-safe: mpesa_checkout_request_id isn't known until the STK push call
-- returns, so a PENDING row briefly exists with it unset. Same pattern as
-- V33's uk_rent_transactions_tenant_external_reference partial index.
CREATE UNIQUE INDEX uk_rent_payment_requests_checkout_request_id
    ON rent_payment_requests (mpesa_checkout_request_id)
    WHERE mpesa_checkout_request_id IS NOT NULL;