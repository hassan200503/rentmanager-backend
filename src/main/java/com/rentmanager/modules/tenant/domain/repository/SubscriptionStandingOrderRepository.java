package com.rentmanager.modules.tenant.domain.repository;

import com.rentmanager.modules.tenant.domain.model.SubscriptionStandingOrder;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionStandingOrderRepository {

    SubscriptionStandingOrder save(SubscriptionStandingOrder order);

    Optional<SubscriptionStandingOrder> findById(UUID id);

    Optional<SubscriptionStandingOrder> findByRatibaResponseRefId(String responseRefId);

    /** Latest order (by creation) for a tenant, if any. */
    Optional<SubscriptionStandingOrder> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
