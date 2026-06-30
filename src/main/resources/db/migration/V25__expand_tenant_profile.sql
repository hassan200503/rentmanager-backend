-- V25__expand_tenant_profile.sql
-- Creates tenant_profile — the renter's profile record under a specific
-- landlord's account. Distinct from `tenants`, which represents the
-- landlord's SaaS account (see TenantProfile.java javadoc for the naming
-- collision explanation).

CREATE TABLE tenant_profile (
                                id              UUID PRIMARY KEY,
                                tenant_id       UUID         NOT NULL,
                                clerk_user_id   VARCHAR(255) NOT NULL,
                                full_name       VARCHAR(255) NOT NULL,
                                email           VARCHAR(255) NOT NULL,
                                phone           VARCHAR(20)  NOT NULL,
                                national_id     VARCHAR(50),

                                CONSTRAINT fk_tenant_profile_tenant
                                    FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

-- A renter (single Clerk identity) may have separate TenantProfile records
-- under different landlords' accounts — so uniqueness is scoped per landlord,
-- NOT global on clerk_user_id alone.
CREATE UNIQUE INDEX idx_tenant_profile_tenant_clerk_user
    ON tenant_profile (tenant_id, clerk_user_id);

CREATE INDEX idx_tenant_profile_clerk_user_id ON tenant_profile (clerk_user_id);