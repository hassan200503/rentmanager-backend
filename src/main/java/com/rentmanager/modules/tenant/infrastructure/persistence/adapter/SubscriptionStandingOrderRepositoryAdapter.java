package com.rentmanager.modules.tenant.infrastructure.persistence.adapter;

import com.rentmanager.modules.tenant.domain.model.SubscriptionStandingOrder;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionStandingOrderRepository;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionStandingOrderJpaEntity;
import com.rentmanager.modules.tenant.infrastructure.persistence.mapper.SubscriptionStandingOrderPersistenceMapper;
import com.rentmanager.modules.tenant.infrastructure.persistence.repository.SubscriptionStandingOrderJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubscriptionStandingOrderRepositoryAdapter
        implements SubscriptionStandingOrderRepository {

    private final SubscriptionStandingOrderJpaRepository jpaRepository;
    private final SubscriptionStandingOrderPersistenceMapper mapper;

    @Override
    public SubscriptionStandingOrder save(SubscriptionStandingOrder order) {
        SubscriptionStandingOrderJpaEntity saved =
                jpaRepository.save(mapper.toJpaEntity(order));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<SubscriptionStandingOrder> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<SubscriptionStandingOrder> findByRatibaResponseRefId(String responseRefId) {
        return jpaRepository.findByRatibaResponseRefId(responseRefId).map(mapper::toDomain);
    }

    @Override
    public Optional<SubscriptionStandingOrder> findFirstByTenantIdOrderByCreatedAtDesc(UUID tenantId) {
        return jpaRepository.findFirstByTenantIdOrderByCreatedAtDesc(tenantId).map(mapper::toDomain);
    }
}
