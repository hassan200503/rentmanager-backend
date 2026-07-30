CREATE TABLE auto_pay_settings (
    id                      UUID         NOT NULL,
    tenant_id               UUID         NOT NULL,
    lease_id                UUID         NOT NULL,
    tenant_profile_id       UUID         NOT NULL,
    enabled                 BOOLEAN      NOT NULL DEFAULT FALSE,
    mpesa_phone             VARCHAR(20),
    last_auto_pay_date      DATE,
    consecutive_failures    INT          NOT NULL DEFAULT 0,
    last_attempt_at         TIMESTAMP,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMP    NOT NULL,
    updated_at              TIMESTAMP    NOT NULL,

    CONSTRAINT pk_auto_pay_settings PRIMARY KEY (id),
    CONSTRAINT fk_auto_pay_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT uq_auto_pay_settings_lease UNIQUE (lease_id)
);

CREATE INDEX idx_auto_pay_settings_enabled ON auto_pay_settings(enabled);
CREATE INDEX idx_auto_pay_settings_tenant_id ON auto_pay_settings(tenant_id);