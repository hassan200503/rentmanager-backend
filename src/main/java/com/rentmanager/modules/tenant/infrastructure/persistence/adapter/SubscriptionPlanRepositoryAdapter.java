package com.rentmanager.modules.tenant.infrastructure.persistence.adapter;

import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.domain.repository.SubscriptionPlanRepository;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPlanEntity;
import com.rentmanager.modules.tenant.infrastructure.persistence.mapper.SubscriptionPlanPersistenceMapper;
import com.rentmanager.modules.tenant.infrastructure.persistence.repository.SubscriptionPlanJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SubscriptionPlanRepositoryAdapter implements SubscriptionPlanRepository {

    private final SubscriptionPlanJpaRepository jpaRepository;
    private final SubscriptionPlanPersistenceMapper mapper;

    public SubscriptionPlanRepositoryAdapter(
            SubscriptionPlanJpaRepository jpaRepository,
            SubscriptionPlanPersistenceMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public SubscriptionPlan save(SubscriptionPlan plan) {
        SubscriptionPlanEntity entity = mapper.toJpaEntity(plan);
        SubscriptionPlanEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public SubscriptionPlan findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found"));
    }

    @Override
    public SubscriptionPlan findByCode(String code) {
        return jpaRepository.findByPlanCode(code)
                .map(mapper::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan not found"));
    }

    @Override
    public List<SubscriptionPlan> findAll() {
        return jpaRepository.findAll()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByPlanCode(code);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }
}