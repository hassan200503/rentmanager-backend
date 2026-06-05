CREATE TABLE leases (
                        id UUID PRIMARY KEY,

                        tenant_id UUID NOT NULL,
                        property_id UUID NOT NULL,
                        unit_id UUID NOT NULL,
                        tenant_profile_id UUID NOT NULL,

                        lease_number VARCHAR(100) NOT NULL UNIQUE,

                        lease_type VARCHAR(30) NOT NULL,
                        billing_cycle VARCHAR(30) NOT NULL,
                        status VARCHAR(30) NOT NULL,

                        start_date DATE NOT NULL,
                        end_date DATE NOT NULL,

                        rent_amount NUMERIC(19,2) NOT NULL,
                        deposit_amount NUMERIC(19,2) NOT NULL,

                        version BIGINT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT now(),
                        updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_lease_tenant ON leases(tenant_id);
CREATE INDEX idx_lease_property ON leases(property_id);
CREATE INDEX idx_lease_unit ON leases(unit_id);
CREATE INDEX idx_lease_status ON leases(status);