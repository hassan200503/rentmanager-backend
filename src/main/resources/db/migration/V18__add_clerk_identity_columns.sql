ALTER TABLE tenants
    ADD COLUMN clerk_org_id VARCHAR(255) UNIQUE;

CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       clerk_user_id VARCHAR(255) UNIQUE NOT NULL,
                       tenant_id UUID,
                       email VARCHAR(255) NOT NULL,
                       first_name VARCHAR(150),
                       last_name VARCHAR(150),
                       active BOOLEAN NOT NULL DEFAULT TRUE,
                       version BIGINT NOT NULL DEFAULT 0,
                       created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                       updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                       CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_users_clerk_user_id ON users(clerk_user_id);
CREATE INDEX idx_users_tenant_id ON users(tenant_id);