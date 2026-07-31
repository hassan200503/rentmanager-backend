package com.rentmanager.modules.tenant.domain.repository;

import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanRepository {

    SubscriptionPlan save(SubscriptionPlan plan);

    Optional<SubscriptionPlan> findById(UUID id);

    Optional<SubscriptionPlan> findByCode(String code);

    List<SubscriptionPlan> findAll();

    boolean existsById(UUID id);

    boolean existsByCode(String code);

    void deleteById(UUID id);
}
