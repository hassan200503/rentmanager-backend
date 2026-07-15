package com.rentmanager.modules.property.application.command.validator;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PropertyMarkPartiallyOccupiedValidator {

    private final PropertyRepository propertyRepository;

    public Property validate(UUID tenantId, UUID propertyId) {

        Property property = propertyRepository.findByIdAndTenantId(
                propertyId,
                tenantId
        ).orElseThrow(() ->
                new IllegalArgumentException("Property not found"));

        if (property.getOccupancyStatus() == null) {
            throw new IllegalArgumentException("Invalid occupancy state");
        }

        if (property.getOccupancyStatus() == OccupancyStatus.PARTIALLY_OCCUPIED) {
            throw new IllegalArgumentException("Property is already partially occupied");
        }

        if (property.getStatus().name().equals("ARCHIVED")) {
            throw new IllegalArgumentException("Archived property cannot be modified");
        }

        return property;
    }
}