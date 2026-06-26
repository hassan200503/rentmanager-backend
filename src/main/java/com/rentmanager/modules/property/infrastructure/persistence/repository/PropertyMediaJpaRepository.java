package com.rentmanager.modules.property.infrastructure.persistence.repository;

import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyMediaJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMediaJpaRepository
        extends JpaRepository<PropertyMediaJpaEntity, UUID> {

    Optional<PropertyMediaJpaEntity> findByIdAndTenantId(
            UUID id,
            UUID tenantId
    );

    Optional<PropertyMediaJpaEntity> findByTenantIdAndPropertyIdAndPrimaryMediaTrue(
            UUID tenantId,
            UUID propertyId
    );

    List<PropertyMediaJpaEntity> findAllByTenantIdAndPropertyId(
            UUID tenantId,
            UUID propertyId
    );

    List<PropertyMediaJpaEntity> findAllByPropertyIdAndTenantId(
            UUID propertyId,
            UUID tenantId
    );

    List<PropertyMediaJpaEntity> findAllByPropertyId(
            UUID propertyId
    );

    List<PropertyMediaJpaEntity> findAllByPropertyIdIn(
            List<UUID> propertyIds
    );
}