package com.rentmanager.modules.property.domain.repository;

import com.rentmanager.modules.property.domain.model.PropertyMedia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyMediaRepository {

    PropertyMedia save(PropertyMedia media);

    Optional<PropertyMedia> findById(UUID id);

    List<PropertyMedia> findAllByPropertyId(UUID propertyId);

    boolean existsById(UUID id);

    void deleteById(UUID id);
}