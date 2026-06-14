CREATE TABLE tenants (
                         id UUID PRIMARY KEY,

                         tenant_code VARCHAR(50) UNIQUE NOT NULL,
                         name VARCHAR(255) NOT NULL,

                         email VARCHAR(255),
                         phone_number VARCHAR(50),

                         type VARCHAR(50),
                         status VARCHAR(50),

                         active BOOLEAN DEFAULT TRUE,
                         onboarding_completed BOOLEAN DEFAULT FALSE,

                         organization_id UUID,

                         currency VARCHAR(10),
                         timezone VARCHAR(50),
                         locale VARCHAR(10),

                         subscription_status VARCHAR(50),
                         active_subscription_id UUID,

                         slug VARCHAR(255) UNIQUE,

                         created_at TIMESTAMP DEFAULT NOW(),
                         updated_at TIMESTAMP DEFAULT NOW()
);