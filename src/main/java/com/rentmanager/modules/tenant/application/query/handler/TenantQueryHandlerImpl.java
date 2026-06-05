package com.rentmanager.modules.tenant.application.query.handler;

import com.rentmanager.modules.tenant.application.query.handler.TenantQueryHandler;
import com.rentmanager.modules.tenant.application.query.projection.TenantListProjection;
import com.rentmanager.modules.tenant.application.query.projection.TenantProjection;
import com.rentmanager.modules.tenant.application.query.service.TenantQueryService;

import java.util.List;
import java.util.UUID;

public class TenantQueryHandlerImpl implements TenantQueryHandler {

    private final TenantQueryService queryService;

    public TenantQueryHandlerImpl(TenantQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public TenantProjection handleGetTenant(UUID tenantId, UUID targetTenantId) {
        return queryService.getTenant(tenantId, targetTenantId);
    }

    @Override
    public List<TenantListProjection> handleGetAll(UUID tenantId) {
        return queryService.getAllTenants(tenantId);
    }

    @Override
    public List<TenantListProjection> handleSearch(UUID tenantId, String keyword) {
        return queryService.searchTenants(tenantId, keyword);
    }
}