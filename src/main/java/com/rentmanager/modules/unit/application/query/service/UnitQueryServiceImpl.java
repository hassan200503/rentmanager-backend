package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.dto.response.UnitSummaryResponse;
import com.rentmanager.modules.unit.application.mapper.UnitMapper;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitQueryServiceImpl implements UnitQueryService {

    private final UnitRepository unitRepository;
    private final UnitMapper unitMapper;

    @Override
    public UnitResponse getById(UUID tenantId, UUID unitId) {
        Unit unit = unitRepository.findByIdAndTenantId(unitId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unit not found for tenant: " + tenantId + ", id: " + unitId
                ));

        return unitMapper.toResponse(unit);
    }

    @Override
    public Page<UnitResponse> getAll(UUID tenantId, Pageable pageable) {
        return unitRepository.findAllByTenantId(tenantId, pageable)
                .map(unitMapper::toResponse);
    }

    @Override
    public Page<UnitResponse> getByProperty(UUID tenantId, UUID propertyId, Pageable pageable) {
        return unitRepository.findByTenantIdAndPropertyId(tenantId, propertyId, pageable)
                .map(unitMapper::toResponse);
    }

    @Override
    public Page<UnitResponse> getByStatus(UUID tenantId, UnitStatus status, Pageable pageable) {
        return unitRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                .map(unitMapper::toResponse);
    }

    @Override
    public Page<UnitResponse> search(UUID tenantId, String keyword, Pageable pageable) {
        return unitRepository.search(tenantId, keyword, pageable)
                .map(unitMapper::toResponse);

    }



    @Override
    public UnitSummaryResponse getSummary(UUID tenantId) {
        long total = unitRepository.countByTenantId(tenantId);
        long vacant = unitRepository.countByTenantIdAndOccupancyStatus(tenantId, UnitOccupancyStatus.VACANT);
        long occupied = unitRepository.countByTenantIdAndOccupancyStatus(tenantId, UnitOccupancyStatus.OCCUPIED);
        long reserved = unitRepository.countByTenantIdAndOccupancyStatus(tenantId, UnitOccupancyStatus.RESERVED);

        return new UnitSummaryResponse(total, vacant, occupied, reserved);
    }


}