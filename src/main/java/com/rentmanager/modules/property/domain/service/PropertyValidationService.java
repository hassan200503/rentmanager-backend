package com.rentmanager.modules.property.domain.service;

import com.rentmanager.modules.property.domain.model.Property;

public interface PropertyValidationService {

    /**
     * Validates if a property can be created under current tenant rules.
     */
    void validateCreation(Property property);

    /**
     * Validates if a property can be updated safely.
     */
    void validateUpdate(Property property);

    /**
     * Validates if a property can transition to ACTIVE state.
     */
    void validateActivation(Property property);

    /**
     * Validates if a property can be archived.
     */
    void validateArchival(Property property);
}