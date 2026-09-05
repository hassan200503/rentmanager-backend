-- V74__add_fk_leases.sql

ALTER TABLE leases
    ADD CONSTRAINT fk_leases_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE leases
    ADD CONSTRAINT fk_leases_property
        FOREIGN KEY (property_id) REFERENCES properties (id);

ALTER TABLE leases
    ADD CONSTRAINT fk_leases_unit
        FOREIGN KEY (unit_id) REFERENCES units (id);

ALTER TABLE leases
    ADD CONSTRAINT fk_leases_tenant_profile
        FOREIGN KEY (tenant_profile_id) REFERENCES tenant_profile (id);
