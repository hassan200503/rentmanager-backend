package com.rentmanager.modules.property.infrastructure.persistence.repository;

import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyCategoryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyCategoryJpaRepository
        extends JpaRepository<PropertyCategoryJpaEntity, UUID> {

    Optional<PropertyCategoryJpaEntity> findByIdAndTenantId(
            UUID id,
            UUID tenantId
    );

    boolean existsByNameAndTenantId(
            String name,
            UUID tenantId
    );

    List<PropertyCategoryJpaEntity> findAllByTenantId(UUID tenantId);

    Optional<PropertyCategoryJpaEntity> findByNameAndTenantId(String name, UUID tenantId);

}
