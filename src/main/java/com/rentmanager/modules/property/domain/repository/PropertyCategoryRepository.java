package com.rentmanager.modules.property.domain.repository;

import com.rentmanager.modules.property.domain.model.PropertyCategory;

import java.util.Optional;
import java.util.UUID;

public interface PropertyCategoryRepository {

    PropertyCategory save(PropertyCategory category);

    Optional<PropertyCategory> findById(UUID id);

    Optional<PropertyCategory> findByNameAndTenantId(String name, UUID tenantId);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}