CREATE TABLE units (
                       id UUID PRIMARY KEY,

                       unit_number VARCHAR(50) NOT NULL,
                       label VARCHAR(255),
                       description TEXT,

                       rent_amount NUMERIC(19,2),

                       tenant_id UUID NOT NULL,
                       property_id UUID NOT NULL,

                       status VARCHAR(30) NOT NULL,
                       occupancy_status VARCHAR(30) NOT NULL,

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for multi-tenant SaaS performance
CREATE INDEX idx_units_tenant_id ON units(tenant_id);
CREATE INDEX idx_units_property_id ON units(property_id);
CREATE INDEX idx_units_tenant_property ON units(tenant_id, property_id);