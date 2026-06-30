package com.rentmanager.modules.tenant.domain.repository;





import com.rentmanager.modules.tenant.domain.model.TenantSubscription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantSubscriptionRepository {

    Optional<TenantSubscription> findById(UUID id);

    List<TenantSubscription> findByTenantId(UUID tenantId);

    TenantSubscription save(TenantSubscription subscription);
}