-- V73__add_fk_units_tenant_and_property.sql
--
-- units.property_id already carries a foreign key on at least one live
-- database (constraint name units_property_id_fkey, ON DELETE CASCADE) —
-- but it was added by hand outside Flyway, so it exists nowhere in migration
-- history and a fresh database (CI, a new developer, production) would not
-- have it. That drift is itself a bug: ON DELETE CASCADE also means
-- deleting a property silently deletes its units with no application-level
-- orchestration or domain event, which is not a behavior this migration
-- intends to keep. Drop the untracked constraint if present and replace it
-- with a properly tracked, non-cascading one.

ALTER TABLE units
    DROP CONSTRAINT IF EXISTS units_property_id_fkey;

ALTER TABLE units
    ADD CONSTRAINT fk_units_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE units
    ADD CONSTRAINT fk_units_property
        FOREIGN KEY (property_id) REFERENCES properties (id);
