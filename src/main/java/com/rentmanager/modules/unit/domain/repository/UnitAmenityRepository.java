package com.rentmanager.modules.unit.domain.repository;

import com.rentmanager.modules.unit.domain.model.UnitAmenity;

import java.util.List;
import java.util.UUID;

public interface UnitAmenityRepository {

    // =========================
    // SAVE
    // =========================
    UnitAmenity save(UnitAmenity amenity);

    // =========================
    // QUERY
    // =========================
    List<UnitAmenity> findByTenantId(UUID tenantId);

    List<UnitAmenity> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    boolean existsByTenantIdAndUnitIdAndName(UUID tenantId, UUID unitId, String name);

    // =========================
    // DELETE
    // =========================
    void delete(UnitAmenity amenity);
}