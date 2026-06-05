package com.rentmanager.modules.tenant.domain.service;

import com.rentmanager.modules.tenant.domain.model.Tenant;

public interface TenantFeatureDomainService {

    boolean isFeatureEnabled(Tenant tenant, String featureKey);

    void enableFeature(Tenant tenant, String featureKey);

    void disableFeature(Tenant tenant, String featureKey);

    void enableDefaultFeatures(Tenant tenant);
}