package com.rentmanager.modules.tenant.domain.service;

import com.rentmanager.modules.tenant.domain.model.Tenant;

public interface TenantQuotaDomainService {

    boolean canCreateUser(Tenant tenant);

    boolean canCreateOrganization(Tenant tenant);

    boolean canCreateProperty(Tenant tenant);

    void validateQuotaOrThrow(Tenant tenant, String quotaType);

    void resetMonthlyQuotas(Tenant tenant);
}