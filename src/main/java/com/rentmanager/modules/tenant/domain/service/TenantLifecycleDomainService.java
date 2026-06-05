package com.rentmanager.modules.tenant.domain.service;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.enums.TenantStatus;

import java.util.UUID;

public interface TenantLifecycleDomainService {

    Tenant activateTenant(Tenant tenant);

    Tenant suspendTenant(Tenant tenant, String reason);

    Tenant deactivateTenant(Tenant tenant, String reason);

    Tenant markForDeletion(Tenant tenant);

    boolean canTransition(TenantStatus from, TenantStatus to);
}