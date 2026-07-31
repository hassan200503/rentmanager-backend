package com.rentmanager.modules.tenant.infrastructure.persistence.repository;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionStandingOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionStandingOrderJpaRepository
        extends JpaRepository<SubscriptionStandingOrderJpaEntity, UUID> {

    Optional<SubscriptionStandingOrderJpaEntity> findByRatibaResponseRefId(String responseRefId);

    Optional<SubscriptionStandingOrderJpaEntity> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
