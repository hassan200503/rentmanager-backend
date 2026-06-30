package com.rentmanager.modules.tenant.domain.service;



import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.Tenant;

public interface TenantPolicyDomainService {

    boolean canCreateUser(Tenant tenant);

    boolean canCreateOrganization(Tenant tenant);

    boolean canAccessFeature(Tenant tenant, String featureKey);

    boolean isUpgradeAllowed(Tenant tenant, SubscriptionPlan targetPlan);

    boolean isDowngradeAllowed(Tenant tenant, SubscriptionPlan targetPlan);

    void validateActionOrThrow(Tenant tenant, String action);
}