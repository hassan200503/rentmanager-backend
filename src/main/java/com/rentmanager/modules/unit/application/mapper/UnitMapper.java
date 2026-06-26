package com.rentmanager.modules.unit.application.mapper;

import com.rentmanager.modules.unit.application.dto.response.PublicUnitResponse;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.domain.model.Unit;
import org.springframework.stereotype.Component;

@Component
public class UnitMapper {






    public UnitResponse toResponse(Unit unit) {

        if (unit == null) return null;

        UnitResponse response = new UnitResponse();

        response.setId(unit.getId());
        response.setTenantId(unit.getTenantId());
        response.setPropertyId(unit.getPropertyId());
        response.setUnitNumber(unit.getUnitNumber());
        response.setFloor(unit.getFloor());
        response.setDescription(unit.getDescription());
        response.setRentAmount(unit.getRentAmount());


        response.setStatus(
                unit.getStatus() != null ? unit.getStatus().name() : null
        );

        response.setOccupancyStatus(
                unit.getOccupancyStatus() != null ? unit.getOccupancyStatus().name() : null
        );

        return response;
    }



    public PublicUnitResponse toPublicResponse(Unit unit) {
        PublicUnitResponse dto = new PublicUnitResponse();
        dto.setId(unit.getId());
        dto.setPropertyId(unit.getPropertyId());
        dto.setUnitNumber(unit.getUnitNumber());
        dto.setDescription(unit.getDescription());
        dto.setRentAmount(unit.getRentAmount());
        dto.setOccupancyStatus(unit.getOccupancyStatus().name());
        return dto;
    }

}