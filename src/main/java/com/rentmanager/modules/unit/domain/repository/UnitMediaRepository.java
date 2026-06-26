package com.rentmanager.modules.unit.domain.repository;

import com.rentmanager.modules.unit.domain.model.UnitMedia;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UnitMediaRepository {

    UnitMedia save(UnitMedia media);

    Optional<UnitMedia> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<UnitMedia> findByTenantIdAndUnitIdAndPrimaryMediaTrue(UUID tenantId, UUID unitId);

    List<UnitMedia> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    List<UnitMedia> findByTenantId(UUID tenantId);

    boolean existsByTenantIdAndUnitIdAndUrl(UUID tenantId, UUID unitId, String url);

    void delete(UnitMedia media);
}