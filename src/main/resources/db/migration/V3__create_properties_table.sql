

CREATE TABLE properties (
                            id UUID PRIMARY KEY,
                            tenant_id UUID NOT NULL,
                            reference_code VARCHAR(255) NOT NULL UNIQUE,
                            name VARCHAR(255) NOT NULL,
                            description TEXT,
                            status VARCHAR(50) NOT NULL,
                            category_id UUID,
                            address_line_1 VARCHAR(255),
                            address_line_2 VARCHAR(255),
                            city VARCHAR(100),
                            state VARCHAR(100),
                            country VARCHAR(100),
                            postal_code VARCHAR(50),
                            created_at TIMESTAMP NOT NULL,
                            updated_at TIMESTAMP,
                            version BIGINT
);

CREATE INDEX idx_property_tenant ON properties(tenant_id);
CREATE INDEX idx_property_reference_code ON properties(reference_code);
CREATE INDEX idx_property_status ON properties(status);