package com.rentmanager.modules.unit.application.mapper;

import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.domain.model.Unit;
import org.springframework.stereotype.Component;

@Component
public class UnitMapper {

    public UnitResponse toResponse(Unit unit) {

        if (unit == null) return null;

        return UnitResponse.builder()
                .id(unit.getId())
                .tenantId(unit.getTenantId())
                .propertyId(unit.getPropertyId())
                .unitNumber(unit.getUnitNumber())
                .floor(unit.getFloor())
                .description(unit.getDescription())
                .rentAmount(unit.getRentAmount())
                .status(unit.getStatus().name())
                .occupancyStatus(unit.getOccupancyStatus().name())
                .build();
    }
}