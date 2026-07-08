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

    // NOTE: retained for backward compatibility — TODO confirm whether these
    // occupancy-only public methods still have callers anywhere before
    // removing them. Public read paths now use the *PubliclyVisible*
    // methods below (see RentManager Public Listings Hardening handoff,
    // 2026-07-08), which additionally enforce UnitStatus.ACTIVE and the
    // parent Property's PropertyStatus.ACTIVE.
    Page<Unit> findByOccupancyStatus(UnitOccupancyStatus occupancyStatus, Pageable pageable);
    Page<Unit> searchPublic(String keyword, UnitOccupancyStatus occupancyStatus, Pageable pageable);
    Optional<Unit> findById(UUID id); // tenant-agnostic — needed for public unit detail page

    /**
     * Tenant-agnostic, row-locking read for the reservation flow.
     * Acquires a PESSIMISTIC_WRITE lock on the unit row for the duration of
     * the caller's transaction, so two concurrent reservation attempts on
     * the same unit serialize instead of both observing VACANT and both
     * proceeding. Callers MUST invoke this within a short-lived transaction
     * — never hold this lock across an external HTTP call (e.g. the Daraja
     * STK push), or concurrent reservation attempts on the unit will queue
     * behind that call's latency.
     */
    Optional<Unit> findByIdForUpdate(UUID id);

    Page<Unit> findByPropertyIdAndOccupancyStatus(UUID propertyId, UnitOccupancyStatus occupancyStatus, Pageable pageable);

    long countByTenantId(UUID tenantId);
    long countByTenantIdAndOccupancyStatus(UUID tenantId, UnitOccupancyStatus occupancyStatus);

    Optional<Unit> findLongestVacant();

    // =========================
    // PUBLIC LISTING HARDENING (2026-07-08)
    //
    // A unit is publicly visible only if its own status is ACTIVE, its
    // occupancyStatus is VACANT, AND its parent property's status is
    // ACTIVE. Deliberate defense-in-depth — do not simplify to a single
    // check (see handoff doc §4).
    // =========================
    Page<Unit> findPubliclyVisibleVacantUnits(String keyword, Pageable pageable);
    Page<Unit> findPubliclyVisibleVacantUnitsByProperty(UUID propertyId, Pageable pageable);
    Optional<Unit> findPubliclyVisibleVacantUnitById(UUID unitId);
    Optional<Unit> findPubliclyVisibleLongestVacantUnit();
}