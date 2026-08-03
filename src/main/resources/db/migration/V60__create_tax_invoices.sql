-- V60__create_tax_invoices.sql
--
-- One tax invoice per residential rent payment (or per commercial rent
-- payment once the VAT branch is enabled). eTIMS requires an invoice per
-- transaction; this table is that invoice record, with the retry bookkeeping
-- the transmission sweeper needs.
--
--   * status lifecycle: PENDING -> TRANSMITTED | FAILED; a landlord's own
--     manual filing (rare for rental receipts) or a bulk concession
--     SELF_FILED. Re-transmission attempts are limited via attempt_count /
--     next_attempt_at (same pattern as disbursement retries).
--   * occurred_at: the transaction's bookkeeping date; eTIMS' required
--     "document date" for a rental receipt is the transaction date.
--   * vat_treatment: STANDARD_RATED (16% commercial), ZERO_RATED, or
--     VAT_EXEMPT (residential). The residential branch is always VAT_EXEMPT
--     (First Schedule Part II para 8 of the VAT Act 2013) — the 16% branch
--     stays dormant until the advisor sign-off (brief item A1).
--   * tenant_kra_pin here is the RENTER'S pin (from tenant_profile.kra_pin)
--     and is optional: only captured when the renter provided it. KRA does
--     not require it on a rental receipt.
--
-- Idempotency: one invoice per (tenant, rent_transaction), enforced by the
-- unique constraint — re-delivered payment events can never double-invoice.

CREATE TABLE tax_invoices (
    id                          UUID PRIMARY KEY,
    tenant_id                   UUID NOT NULL REFERENCES tenants (id),
    rent_transaction_id         UUID NOT NULL REFERENCES rent_transactions (id),
    ledger_entry_id             UUID NOT NULL REFERENCES rent_ledger_entries (id),
    lease_id                    UUID NOT NULL REFERENCES leases (id),
    tenant_profile_id           UUID REFERENCES tenant_profile (id),
    landlord_kra_pin            VARCHAR(30),
    tenant_kra_pin              VARCHAR(30),
    premises_type               VARCHAR(30) NOT NULL,
    vat_treatment               VARCHAR(40) NOT NULL,
    status                      VARCHAR(30) NOT NULL,
    amount                      NUMERIC(19,2) NOT NULL,
    external_reference          VARCHAR(255),
    source                      VARCHAR(30) NOT NULL,
    occurred_at                 TIMESTAMPTZ NOT NULL,
    kra_control_number          VARCHAR(255),
    qr_code_data                TEXT,
    receipt_signature           TEXT,
    sequential_receipt_number   VARCHAR(80),
    transmitted_at              TIMESTAMPTZ,
    attempt_count               INT NOT NULL DEFAULT 0,
    next_attempt_at             TIMESTAMPTZ,
    last_error                  VARCHAR(500),
    version                     BIGINT NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_tax_invoice_per_transaction
        UNIQUE (tenant_id, rent_transaction_id),
    CONSTRAINT ck_tax_invoice_status
        CHECK (status IN ('PENDING', 'TRANSMITTED', 'FAILED', 'SELF_FILED')),
    CONSTRAINT ck_tax_invoice_premises_type
        CHECK (premises_type IN ('RESIDENTIAL', 'COMMERCIAL')),
    CONSTRAINT ck_tax_invoice_vat_treatment
        CHECK (vat_treatment IN ('STANDARD_RATED', 'ZERO_RATED', 'VAT_EXEMPT'))
);

CREATE INDEX idx_tax_invoices_due
    ON tax_invoices (status, attempt_count, next_attempt_at);