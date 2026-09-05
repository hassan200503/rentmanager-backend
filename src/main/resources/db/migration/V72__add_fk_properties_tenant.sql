-- V72__add_fk_properties_tenant.sql
--
-- properties.tenant_id had no foreign key, so a deleted/mistyped tenant id
-- could silently orphan properties. No ON DELETE clause, matching the
-- existing fk_tenant_profile_tenant convention (V25) — deleting a tenant
-- that still owns properties is a bug to catch at the DB, not something to
-- cascade away silently.

ALTER TABLE properties
    ADD CONSTRAINT fk_properties_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);
