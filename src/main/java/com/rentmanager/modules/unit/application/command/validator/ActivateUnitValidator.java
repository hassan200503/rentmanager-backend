package com.rentmanager.modules.unit.application.command.validator;

import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ActivateUnitValidator {

    private final UnitRepository unitRepository;

    public Unit validate(UUID tenantId, UUID unitId) {

        Unit unit = unitRepository.findByIdAndTenantId(unitId, tenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unit not found"));

        if (unit.getStatus() == UnitStatus.ACTIVE) {
            throw new IllegalArgumentException("Unit already active");
        }

        if (unit.getStatus() == UnitStatus.ARCHIVED) {
            throw new IllegalArgumentException("Archived unit cannot be activated");
        }

        return unit;
    }
}