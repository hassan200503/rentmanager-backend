package com.rentmanager.modules.tenant.domain.service;



import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.Tenant;

public interface TenantBillingOrchestrationDomainService {

    void upgradeSubscription(Tenant tenant, SubscriptionPlan newPlan);

    void downgradeSubscription(Tenant tenant, SubscriptionPlan newPlan);

    void cancelSubscription(Tenant tenant);

    void renewSubscription(Tenant tenant);

    void applyProration(Tenant tenant, SubscriptionPlan fromPlan, SubscriptionPlan toPlan);
}