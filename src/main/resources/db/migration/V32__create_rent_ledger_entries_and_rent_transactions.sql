-- V32__create_rent_ledger_entries_and_rent_transactions.sql

CREATE TABLE rent_ledger_entries (
                                     id                    UUID PRIMARY KEY,
                                     tenant_id             UUID NOT NULL,
                                     lease_id              UUID NOT NULL,
                                     unit_id               UUID NOT NULL,
                                     tenant_profile_id     UUID NOT NULL,
                                     billing_period_start  DATE NOT NULL,
                                     billing_period_end    DATE NOT NULL,
                                     due_date              DATE NOT NULL,
                                     amount_due            NUMERIC(19,2) NOT NULL,
                                     amount_paid           NUMERIC(19,2) NOT NULL DEFAULT 0,
                                     status                VARCHAR(32) NOT NULL,
                                     prorated              BOOLEAN NOT NULL DEFAULT FALSE,
                                     version               BIGINT NOT NULL DEFAULT 0,
                                     created_at            TIMESTAMP NOT NULL,
                                     updated_at            TIMESTAMP NOT NULL,

                                     CONSTRAINT uk_rent_ledger_entries_lease_period UNIQUE (lease_id, billing_period_start)
);

CREATE INDEX idx_rent_ledger_entries_tenant_id ON rent_ledger_entries (tenant_id);
CREATE INDEX idx_rent_ledger_entries_tenant_status_due_date ON rent_ledger_entries (tenant_id, status, due_date);

CREATE TABLE rent_transactions (
                                   id                   UUID PRIMARY KEY,
                                   tenant_id            UUID NOT NULL,
                                   ledger_entry_id      UUID NOT NULL REFERENCES rent_ledger_entries (id),
                                   lease_id             UUID NOT NULL,
                                   type                 VARCHAR(32) NOT NULL,
                                   amount               NUMERIC(19,2) NOT NULL,
                                   external_reference   VARCHAR(255),
                                   source               VARCHAR(32) NOT NULL,
                                   recorded_by          VARCHAR(255) NOT NULL,
                                   occurred_at          TIMESTAMP NOT NULL,
                                   version              BIGINT NOT NULL DEFAULT 0,
                                   created_at           TIMESTAMP NOT NULL,
                                   updated_at           TIMESTAMP NOT NULL
);

CREATE INDEX idx_rent_transactions_tenant_id ON rent_transactions (tenant_id);
CREATE INDEX idx_rent_transactions_ledger_entry_id ON rent_transactions (ledger_entry_id);
CREATE INDEX idx_rent_transactions_lease_id ON rent_transactions (lease_id);
CREATE INDEX idx_rent_transactions_external_reference ON rent_transactions (external_reference);