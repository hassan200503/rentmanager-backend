package com.rentmanager.modules.tenant.infrastructure.persistence.repository;

import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {

    Optional<TenantEntity> findBySlug(String slug);

    Optional<TenantEntity> findByTenantCode(String tenantCode);

    List<TenantEntity> findByNameContainingIgnoreCaseOrTenantCodeContainingIgnoreCase(
            String name,
            String code
    );

    boolean existsBySlug(String slug);

    // ✅ ADDED — required by domain + adapter
    boolean existsByTenantCode(String tenantCode);
}