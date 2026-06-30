package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.unit.application.dto.response.UnitReservationSummaryResponse;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitReservationSummaryQueryServiceImpl implements UnitReservationSummaryQueryService {

    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;

    private static final int DEPOSIT_MONTHS = 2;

    @Override
    public UnitReservationSummaryResponse getSummary(UUID unitId) {

        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Unit not found", ErrorCode.UNIT_NOT_FOUND
                ));

        Property property = propertyRepository.findById(unit.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found", ErrorCode.PROPERTY_NOT_FOUND
                ));

        BigDecimal deposit = unit.getRentAmount()
                .multiply(BigDecimal.valueOf(DEPOSIT_MONTHS));

        return UnitReservationSummaryResponse.builder()
                .unitId(unit.getId())
                .unitNumber(unit.getUnitNumber())
                .propertyName(property.getName())
                .monthlyRent(unit.getRentAmount())
                .depositAmount(deposit)
                .build();
    }
}