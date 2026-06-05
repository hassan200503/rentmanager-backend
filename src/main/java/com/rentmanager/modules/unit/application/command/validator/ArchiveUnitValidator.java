package com.rentmanager.modules.unit.application.command.validator;

import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ArchiveUnitValidator {

    private final UnitRepository unitRepository;

    public Unit validate(UUID tenantId, UUID unitId) {

        Unit unit = unitRepository.findByIdAndTenantId(unitId, tenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unit not found"));

        if (unit.getStatus() != null &&
            unit.getStatus().name().equals("ARCHIVED")) {
            throw new IllegalArgumentException("Unit already archived");
        }

        return unit;
    }
}