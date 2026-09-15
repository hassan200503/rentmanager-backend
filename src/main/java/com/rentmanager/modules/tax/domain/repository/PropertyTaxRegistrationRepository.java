package com.rentmanager.modules.tax.domain.repository;

import com.rentmanager.modules.tax.domain.model.PropertyTaxRegistration;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PropertyTaxRegistrationRepository {

    Optional<PropertyTaxRegistration> findById(UUID id);

    Optional<PropertyTaxRegistration> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId);

    List<PropertyTaxRegistration> findAll();

    List<PropertyTaxRegistration> findAllByTenantId(UUID tenantId);

    PropertyTaxRegistration save(PropertyTaxRegistration registration);
}
