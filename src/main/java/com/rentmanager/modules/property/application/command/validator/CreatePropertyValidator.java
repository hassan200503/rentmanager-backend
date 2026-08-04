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

        validatePremisesClassification(request);
    }

    /**
     * Premises classification is a legal/tax attribute (MRI vs 16% VAT
     * branch), so an explicit override must always carry a justification.
     * The domain factory re-enforces these rules at persist time; this guard
     * fails fast with a clean API error before reaching persistence.
     */
    private void validatePremisesClassification(CreatePropertyRequest request) {
        if (request.getPremisesType() == null) {
            if (request.getPremisesTypeOverrideReason() != null
                    && !request.getPremisesTypeOverrideReason().isBlank()) {
                throw new IllegalArgumentException(
                        "premisesTypeOverrideReason cannot be provided without an explicit premisesType"
                );
            }
            return;
        }

        String reason = request.getPremisesTypeOverrideReason();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "premisesTypeOverrideReason is required when premisesType is provided"
            );
        }
        if (reason.length() > 500) {
            throw new IllegalArgumentException(
                    "premisesTypeOverrideReason must not exceed 500 characters"
            );
        }
    }
}