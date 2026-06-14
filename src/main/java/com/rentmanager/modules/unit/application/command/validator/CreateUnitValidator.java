package com.rentmanager.modules.unit.application.command.validator;

import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CreateUnitValidator {

    private final UnitRepository unitRepository;

    public void validate(UUID tenantId, CreateUnitRequest request) {

        if (request == null) {
            throw new IllegalArgumentException("Request is required");
        }

        if (request.getUnitNumber() == null || request.getUnitNumber().isBlank()) {
            throw new IllegalArgumentException("Unit number is required");
        }

        String unitNumber = request.getUnitNumber().trim();

        // FIX: remove race-condition unsafe pre-check
        // DB unique constraint is the source of truth
        // validator only handles structural validation
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant is required");
        }

        if (unitNumber.isBlank()) {
            throw new IllegalArgumentException("Unit number is required");
        }
    }
}