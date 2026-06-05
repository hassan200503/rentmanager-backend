package com.rentmanager.modules.tenant.infrastructure.persistence.adapter;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * TenantSubscription is NOT a standalone aggregate.
 *
 * RULE:
 * - Subscription data lives inside Tenant aggregate
 * - All changes must go through Tenant repository
 */
@Component
public class TenantSubscriptionRepositoryAdapter {

    private final TenantRepository tenantRepository;

    public TenantSubscriptionRepositoryAdapter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * Assign a subscription to a tenant
     */
    public void assignSubscription(UUID tenantId, UUID subscriptionId) {

        Tenant tenant = findTenant(tenantId);

        tenant.assignSubscription(subscriptionId);

        tenantRepository.save(tenant);
    }

    /**
     * Update subscription status (ACTIVE, TRIAL, EXPIRED, CANCELLED)
     */
    public void updateSubscriptionStatus(
            UUID tenantId,
            SubscriptionStatus status
    ) {

        Tenant tenant = findTenant(tenantId);

        tenant.updateSubscriptionStatus(status);

        tenantRepository.save(tenant);
    }

    /**
     * Remove active subscription
     */
    public void removeSubscription(UUID tenantId) {

        Tenant tenant = findTenant(tenantId);

        tenant.assignSubscription(null);

        tenant.updateSubscriptionStatus(SubscriptionStatus.TRIAL);

        tenantRepository.save(tenant);
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }
}