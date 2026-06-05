package com.rentmanager.modules.property.application.command.validator;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArchivePropertyValidator {

    private final PropertyRepository propertyRepository;

    public Property validate(UUID tenantId,
                             UUID propertyId) {

        Property property = propertyRepository.findByIdAndTenantId(
                propertyId,
                tenantId
        ).orElseThrow(() ->
                new IllegalArgumentException("Property not found"));

        if (property.isArchived()) {
            throw new IllegalArgumentException(
                    "Property is already archived"
            );
        }

        return property;
    }
}