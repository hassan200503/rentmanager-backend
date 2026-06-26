package com.rentmanager.modules.property.domain.repository;

import com.rentmanager.modules.property.domain.model.PropertyMedia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMediaRepository {

    PropertyMedia save(PropertyMedia media);

    Optional<PropertyMedia> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PropertyMedia> findByTenantIdAndPropertyIdAndPrimaryMediaTrue(
            UUID tenantId,
            UUID propertyId
    );

    List<PropertyMedia> findAllByTenantIdAndPropertyId(
            UUID tenantId,
            UUID propertyId
    );

    void delete(PropertyMedia media);
}