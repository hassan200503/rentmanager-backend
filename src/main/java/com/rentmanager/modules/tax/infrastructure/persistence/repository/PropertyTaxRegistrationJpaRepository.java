package com.rentmanager.modules.tax.infrastructure.persistence.repository;

import com.rentmanager.modules.tax.infrastructure.persistence.entity.PropertyTaxRegistrationJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyTaxRegistrationJpaRepository
        extends JpaRepository<PropertyTaxRegistrationJpaEntity, UUID> {

    Optional<PropertyTaxRegistrationJpaEntity> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId);
}
