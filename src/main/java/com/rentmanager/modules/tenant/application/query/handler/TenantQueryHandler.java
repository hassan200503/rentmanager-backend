package com.rentmanager.modules.tenant.application.query.handler;

import com.rentmanager.modules.tenant.application.query.projection.TenantListProjection;
import com.rentmanager.modules.tenant.application.query.projection.TenantProjection;

import java.util.List;
import java.util.UUID;

public interface TenantQueryHandler {

    TenantProjection handleGetTenant(UUID tenantId, UUID targetTenantId);

    List<TenantListProjection> handleGetAll(UUID tenantId);

    List<TenantListProjection> handleSearch(UUID tenantId, String keyword);
}