package com.rentmanager.modules.unit.domain.repository;

import com.rentmanager.modules.unit.domain.model.UnitMedia;

import java.util.List;
import java.util.UUID;

public interface UnitMediaRepository {

    UnitMedia save(UnitMedia media);

    List<UnitMedia> findByTenantId(UUID tenantId);

    List<UnitMedia> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    boolean existsByTenantIdAndUnitIdAndUrl(UUID tenantId, UUID unitId, String url);

    void delete(UnitMedia media);
}