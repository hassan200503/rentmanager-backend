-- V75__add_fk_rent_ledger_entries.sql

ALTER TABLE rent_ledger_entries
    ADD CONSTRAINT fk_rent_ledger_entries_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE rent_ledger_entries
    ADD CONSTRAINT fk_rent_ledger_entries_lease
        FOREIGN KEY (lease_id) REFERENCES leases (id);

ALTER TABLE rent_ledger_entries
    ADD CONSTRAINT fk_rent_ledger_entries_unit
        FOREIGN KEY (unit_id) REFERENCES units (id);

ALTER TABLE rent_ledger_entries
    ADD CONSTRAINT fk_rent_ledger_entries_tenant_profile
        FOREIGN KEY (tenant_profile_id) REFERENCES tenant_profile (id);
