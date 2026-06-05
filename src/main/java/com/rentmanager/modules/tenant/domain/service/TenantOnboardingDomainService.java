package com.rentmanager.modules.tenant.domain.service;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.model.TenantSettings;
import com.rentmanager.modules.tenant.domain.model.Organization;

public interface TenantOnboardingDomainService {

    Tenant onboardTenant(Tenant tenant,
                         Organization organization,
                         TenantSettings settings);

    void initializeDefaultRoles(Tenant tenant);

    void initializeDefaultFeatures(Tenant tenant);

    void initializeDefaultSubscription(Tenant tenant);
}