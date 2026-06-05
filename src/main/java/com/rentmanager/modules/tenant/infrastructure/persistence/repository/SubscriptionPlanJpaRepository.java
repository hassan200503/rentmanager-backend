package com.rentmanager.modules.tenant.infrastructure.persistence.repository;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionPlanJpaRepository extends JpaRepository<SubscriptionPlanEntity, UUID> {

    Optional<SubscriptionPlanEntity> findByPlanCode(String planCode);

    boolean existsByPlanCode(String planCode);
}