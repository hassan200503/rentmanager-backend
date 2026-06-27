package com.rentmanager.modules.unit.application.query.service;

import com.rentmanager.modules.unit.application.dto.response.PublicUnitResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PublicUnitQueryService {
    Page<PublicUnitResponse> getVacantUnits(String keyword, Pageable pageable);
    Page<PublicUnitResponse> getVacantUnitsByProperty(UUID propertyId, Pageable pageable);
    PublicUnitResponse getVacantUnitById(UUID unitId);

    PublicUnitResponse getLongestVacantUnit();
}