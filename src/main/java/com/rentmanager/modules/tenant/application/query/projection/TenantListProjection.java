package com.rentmanager.modules.tenant.application.query.projection;

import java.util.UUID;

public class TenantListProjection {

    private UUID tenantId;
    private String tenantCode;
    private String name;
    private String slug;
    private String status;

    public TenantListProjection(
            UUID tenantId,
            String tenantCode,
            String name,
            String slug,
            String status
    ) {
        this.tenantId = tenantId;
        this.tenantCode = tenantCode;
        this.name = name;
        this.slug = slug;
        this.status = status;
    }

    // getters
}