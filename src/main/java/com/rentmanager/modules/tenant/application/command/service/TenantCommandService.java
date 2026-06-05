package com.rentmanager.modules.tenant.application.command.service;

import com.rentmanager.modules.tenant.application.dto.request.*;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;

import java.util.UUID;

public interface TenantCommandService {

    TenantResponse createTenant(UUID tenantId, CreateTenantRequest request);

    TenantResponse suspendTenant(UUID tenantId, UUID targetTenantId, SuspendTenantRequest request);

    TenantResponse activateTenant(UUID tenantId, UUID targetTenantId);

    TenantResponse updateTenantStatus(UUID tenantId, UUID targetTenantId, UpdateTenantStatusRequest request);

    TenantResponse updateTenantSubscription(UUID tenantId, UUID targetTenantId, UpdateTenantSubscriptionRequest request);

    TenantResponse updateTenantBranding(UUID tenantId, UUID targetTenantId, UpdateTenantBrandingRequest request);

    TenantResponse assignTenantOwner(UUID tenantId, UUID targetTenantId, AssignTenantOwnerRequest request);

    TenantResponse changeTenantOwner(UUID tenantId, UUID targetTenantId, ChangeTenantOwnerRequest request);

    TenantResponse getTenant(UUID tenantId, UUID targetTenantId);
}