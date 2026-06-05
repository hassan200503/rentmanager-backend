package com.rentmanager.modules.property.domain.repository;

import com.rentmanager.modules.property.domain.model.PropertyAmenity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyAmenityRepository {

    PropertyAmenity save(PropertyAmenity amenity);

    Optional<PropertyAmenity> findById(UUID id);

    List<PropertyAmenity> findAllByPropertyId(UUID propertyId);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}