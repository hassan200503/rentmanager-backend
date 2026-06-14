package com.rentmanager.domain.base;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.util.UUID;

@MappedSuperclass
public abstract class BaseTenantEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /**
     * Assign tenant once only (enforces tenant isolation safety).
     */
    public void assignTenant(UUID tenantId) {
        if (this.tenantId != null) {
            throw new IllegalStateException("Tenant already assigned");
        }
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    protected void restoreTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }




    /**
     *
     * FIX: removed public setter to prevent tenant override
     * (critical SaaS isolation rule)
     */
}