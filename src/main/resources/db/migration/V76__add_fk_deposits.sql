-- V76__add_fk_deposits.sql

ALTER TABLE deposits
    ADD CONSTRAINT fk_deposits_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE deposits
    ADD CONSTRAINT fk_deposits_lease
        FOREIGN KEY (lease_id) REFERENCES leases (id);

ALTER TABLE deposits
    ADD CONSTRAINT fk_deposits_unit
        FOREIGN KEY (unit_id) REFERENCES units (id);

ALTER TABLE deposits
    ADD CONSTRAINT fk_deposits_tenant_profile
        FOREIGN KEY (tenant_profile_id) REFERENCES tenant_profile (id);
