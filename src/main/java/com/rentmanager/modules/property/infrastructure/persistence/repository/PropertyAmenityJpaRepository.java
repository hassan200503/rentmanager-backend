package com.rentmanager.modules.property.infrastructure.persistence.repository;

import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyAmenityJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyAmenityJpaRepository
        extends JpaRepository<PropertyAmenityJpaEntity, UUID> {

    Optional<PropertyAmenityJpaEntity> findByIdAndTenantId(
            UUID id,
            UUID tenantId
    );

    List<PropertyAmenityJpaEntity> findAllByTenantId(UUID tenantId);

    boolean existsByNameAndTenantId(
            String name,
            UUID tenantId
    );

    Optional<PropertyAmenityJpaEntity> findByNameAndTenantId(String name, UUID tenantId);




    List<PropertyAmenityJpaEntity> findAllByPropertyId(UUID propertyId);
}