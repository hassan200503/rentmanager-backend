package com.rentmanager.modules.tenant.application.query.service;

import com.rentmanager.modules.tenant.application.query.projection.TenantListProjection;
import com.rentmanager.modules.tenant.application.query.projection.TenantProjection;

import java.util.List;
import java.util.UUID;

public interface TenantQueryService {

    TenantProjection getTenant(UUID tenantId, UUID targetTenantId);

    List<TenantListProjection> getAllTenants(UUID tenantId);

    List<TenantListProjection> searchTenants(UUID tenantId, String keyword);
}