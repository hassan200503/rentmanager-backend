-- Business rule: prevent duplicate unit numbers per property per tenant
ALTER TABLE units
    ADD CONSTRAINT uk_units_tenant_property_unit_number
        UNIQUE (tenant_id, property_id, unit_number);