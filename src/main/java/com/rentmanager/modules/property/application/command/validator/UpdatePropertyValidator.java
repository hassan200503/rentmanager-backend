package com.rentmanager.modules.property.application.command.validator;

import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UpdatePropertyValidator {

    private final PropertyRepository propertyRepository;

    public void validate(UUID tenantId,
                         UUID propertyId,
                         UpdatePropertyRequest request) {

        boolean exists =
                propertyRepository.existsByIdAndTenantId(
                        propertyId,
                        tenantId
                );

        if (!exists) {
            throw new IllegalArgumentException(
                    "Property not found"
            );
        }

        if (request.getName() == null ||
            request.getName().isBlank()) {

            throw new IllegalArgumentException(
                    "Property name is required"
            );
        }
    }
}