package com.rentmanager.modules.unit.domain.repository;

import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface UnitRepository {

    // =========================
    // CORE TENANT SCOPED LOOKUPS
    // =========================

    Optional<Unit> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Unit> findAllByTenantId(UUID tenantId, Pageable pageable);

    // =========================
    // BUSINESS QUERIES
    // =========================

    boolean existsByTenantIdAndUnitNumber(UUID tenantId, String unitNumber);

    Page<Unit> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId, Pageable pageable);

    Page<Unit> findByTenantIdAndStatus(UUID tenantId, UnitStatus status, Pageable pageable);

    // =========================
    // SEARCH
    // =========================
    Page<Unit> search(UUID tenantId, String keyword, Pageable pageable);

    // =========================
    // WRITE OPERATIONS
    // =========================
    Unit save(Unit unit);

    void delete(Unit unit);






    Page<Unit> findByOccupancyStatus(UnitOccupancyStatus occupancyStatus, Pageable pageable);
    Page<Unit> searchPublic(String keyword, UnitOccupancyStatus occupancyStatus, Pageable pageable);
    Optional<Unit> findById(UUID id); // tenant-agnostic — needed for public unit detail page
    Page<Unit> findByPropertyIdAndOccupancyStatus(UUID propertyId, UnitOccupancyStatus occupancyStatus, Pageable pageable);
}