CREATE TABLE maintenance_requests (
    id                  UUID         NOT NULL,
    tenant_id           UUID         NOT NULL,
    unit_id             UUID         NOT NULL,
    property_id         UUID         NOT NULL,
    tenant_profile_id   UUID         NOT NULL,
    lease_id            UUID,
    title               VARCHAR(200) NOT NULL,
    description         TEXT,
    category            VARCHAR(50)  NOT NULL,
    priority            VARCHAR(50)  NOT NULL,
    status              VARCHAR(50)  NOT NULL DEFAULT 'SUBMITTED',
    scheduled_date      DATE,
    completed_at        TIMESTAMP,
    notes               TEXT,
    created_by          VARCHAR(100),
    assigned_to         VARCHAR(100),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,

    CONSTRAINT pk_maintenance_requests PRIMARY KEY (id),
    CONSTRAINT fk_maintenance_requests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
);

CREATE INDEX idx_maintenance_requests_tenant_id ON maintenance_requests(tenant_id);
CREATE INDEX idx_maintenance_requests_unit_id ON maintenance_requests(unit_id);
CREATE INDEX idx_maintenance_requests_status ON maintenance_requests(status);
CREATE INDEX idx_maintenance_requests_tenant_profile_id ON maintenance_requests(tenant_profile_id);
