package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UnitQueryService {

    UnitResponse getById(UUID tenantId, UUID unitId);

    Page<UnitResponse> getAll(UUID tenantId, Pageable pageable);

    Page<UnitResponse> getByProperty(UUID tenantId, UUID propertyId, Pageable pageable);

    Page<UnitResponse> getByStatus(UUID tenantId, UnitStatus status, Pageable pageable);

    Page<UnitResponse> search(UUID tenantId, String keyword, Pageable pageable);
}