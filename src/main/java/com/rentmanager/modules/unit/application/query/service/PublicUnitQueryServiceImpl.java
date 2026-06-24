package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.unit.application.dto.response.PublicUnitResponse;
import com.rentmanager.modules.unit.application.mapper.UnitMapper;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PublicUnitQueryServiceImpl implements PublicUnitQueryService {

    private final UnitRepository unitRepository;
    private final UnitMapper unitMapper;

    @Override
    public Page<PublicUnitResponse> getVacantUnits(String keyword, Pageable pageable) {
        Page<Unit> units = (keyword == null || keyword.isBlank())
                ? unitRepository.findByOccupancyStatus(UnitOccupancyStatus.VACANT, pageable)
                : unitRepository.searchPublic(keyword, UnitOccupancyStatus.VACANT, pageable);

        return units.map(unitMapper::toPublicResponse);
    }

    @Override
    public Page<PublicUnitResponse> getVacantUnitsByProperty(UUID propertyId, Pageable pageable) {
        return unitRepository
                .findByPropertyIdAndOccupancyStatus(
                        propertyId,
                        UnitOccupancyStatus.VACANT,
                        pageable
                )
                .map(unitMapper::toPublicResponse);
    }

    @Override
    public PublicUnitResponse getVacantUnitById(UUID unitId) {

        Unit unit = unitRepository.findById(unitId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Unit not found",
                                ErrorCode.UNIT_NOT_FOUND
                        )
                );

        if (unit.getOccupancyStatus() != UnitOccupancyStatus.VACANT) {
            throw new ResourceNotFoundException(
                    "Unit not found",
                    ErrorCode.UNIT_NOT_FOUND
            );
        }

        return unitMapper.toPublicResponse(unit);
    }
}