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

        if (request.getUnitNumber() == null || request.getUnitNumber().isBlank()) {
            throw new IllegalArgumentException("Unit number is required");
        }

        if (unitRepository.existsByTenantIdAndUnitNumber(
                tenantId,
                request.getUnitNumber()
        )) {
            throw new IllegalArgumentException("Unit already exists for tenant");
        }
    }
}