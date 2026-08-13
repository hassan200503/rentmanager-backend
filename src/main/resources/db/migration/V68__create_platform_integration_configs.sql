-- Platform Integrations Control Plane: per-environment, encrypted, registry-mediated
-- provider configuration owned by the platform owner (see AUDIT.md and the Integrations
-- feature in the Platform Admin Console).

CREATE TABLE platform_integration_configs (
    id UUID PRIMARY KEY,
    provider_key VARCHAR(50) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    encrypted_credentials TEXT NOT NULL,
    key_version INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_CONFIGURED',
    last_verified_at TIMESTAMPTZ,
    last_verified_by VARCHAR(200),
    last_error TEXT,
    updated_by VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_integration_provider_env UNIQUE (provider_key, environment)
);

CREATE INDEX idx_integration_configs_active ON platform_integration_configs (is_active);

-- Append-only audit trail for integration actions. Secret values are NEVER stored
-- here -- only redacted diffs (see SecretRedactor in the integrations module).
CREATE TABLE integration_audit_log (
    id UUID PRIMARY KEY,
    provider_key VARCHAR(50) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    action VARCHAR(40) NOT NULL,
    actor_user_id VARCHAR(200) NOT NULL,
    metadata TEXT,
    ip_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_integration_audit_provider ON integration_audit_log (provider_key, created_at DESC);