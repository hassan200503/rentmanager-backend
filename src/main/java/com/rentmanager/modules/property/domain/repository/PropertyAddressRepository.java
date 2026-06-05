package com.rentmanager.modules.property.domain.repository;

import com.rentmanager.modules.property.domain.model.PropertyAddress;

import java.util.Optional;
import java.util.UUID;

public interface PropertyAddressRepository {

    PropertyAddress save(PropertyAddress address);

    Optional<PropertyAddress> findById(UUID id);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}