package com.rentmanager.modules.unit.application.query.handler;

import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.application.mapper.UnitMapper;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UnitQueryHandler {

    private final UnitRepository unitRepository;
    private final UnitMapper unitMapper;

    public UnitResponse handleGetById(UUID tenantId, UUID unitId) {

        Unit unit = unitRepository.findByIdAndTenantId(unitId, tenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unit not found"));

        return unitMapper.toResponse(unit);
    }

    public List<UnitResponse> handleGetAll(UUID tenantId, int page, int size) {

        Pageable pageable = PageRequest.of(page, size);

        Page<Unit> result =
                unitRepository.findAllByTenantId(tenantId, pageable);

        return result.stream()
                .map(unitMapper::toResponse)
                .toList();
    }
}