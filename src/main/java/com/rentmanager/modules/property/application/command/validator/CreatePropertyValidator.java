package com.rentmanager.modules.property.application.command.validator;

import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreatePropertyValidator {


    private final PropertyRepository propertyRepository;

    public void validate(UUID tenantId,
                         CreatePropertyRequest request) {

        if (tenantId == null) {
            throw new IllegalArgumentException("TenantId is required");
        }

        if (request == null ||
                request.getName() == null ||
                request.getName().isBlank()) {

            throw new IllegalArgumentException(
                    "Property name is required"
            );
        }

        boolean exists =
                propertyRepository.existsByTenantIdAndNameIgnoreCase(
                        tenantId,
                        request.getName()
                );

        if (exists) {
            // IMPORTANT: still safe exception type,
            // but now clearly a business rule violation
            throw new IllegalStateException(
                    "Property already exists for tenant"
            );
        }
    }
}