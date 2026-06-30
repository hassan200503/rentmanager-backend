package com.rentmanager.modules.tenant.domain.service;


import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.model.TenantSubscription;

import java.time.LocalDate;

public interface SubscriptionDomainService {

    TenantSubscription createTrialSubscription(
            String tenantCode,
            SubscriptionPlan plan,
            LocalDate startDate
    );

    TenantSubscription upgradeSubscription(
            TenantSubscription current,
            SubscriptionPlan newPlan
    );

    void validateSubscriptionActivation(TenantSubscription subscription);

    void validateSubscriptionExpiration(TenantSubscription subscription);

    boolean isSubscriptionActive(TenantSubscription subscription);
}