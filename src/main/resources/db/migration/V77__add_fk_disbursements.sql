-- V77__add_fk_disbursements.sql
--
-- lease_id and ledger_entry_id are nullable on this table (a disbursement
-- isn't always tied to a specific lease/ledger entry) — a FK still applies,
-- Postgres simply doesn't check it when the column is NULL.

ALTER TABLE disbursements
    ADD CONSTRAINT fk_disbursements_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE disbursements
    ADD CONSTRAINT fk_disbursements_lease
        FOREIGN KEY (lease_id) REFERENCES leases (id);

ALTER TABLE disbursements
    ADD CONSTRAINT fk_disbursements_ledger_entry
        FOREIGN KEY (ledger_entry_id) REFERENCES rent_ledger_entries (id);
