package com.rentmanager.modules.lease.domain.repository;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.*;
import java.util.UUID;

/**
 * Domain Repository Contract (Aligned with ACL implementation)
 *
 * NOTE:
 * - Implementation uses Specification-based querying
 * - Keep this interface aligned with infrastructure adapter
 * - No duplication of derived query methods here
 */
public interface LeaseRepository {

    Lease save(Lease lease);

    Optional<Lease> findById(UUID id);

    List<Lease> findAllByTenant(UUID tenantId);

    List<Lease> findActiveByTenant(UUID tenantId);

    List<Lease> findByProperty(UUID tenantId, UUID propertyId);

    void delete(UUID id);


    /**
     * Finds leases in a given status whose start date is today or earlier.
     * Used by the automatic lease activation scheduler.
     */
    List<Lease> findAllByStatusAndStartDateLessThanEqual(
            LeaseStatus status,
            LocalDate date
    );

    /**
     * NEW: finds leases in any of the given statuses, of any of the given
     * lease types, whose end date is today or earlier. Used by the
     * automatic lease expiry scheduler. MONTH_TO_MONTH is intentionally
     * excluded at the call site (scheduler), not baked into this query's
     * name — this method stays a general-purpose building block.
     */
    List<Lease> findAllByStatusInAndLeaseTypeInAndEndDateLessThanEqual(
            List<LeaseStatus> statuses,
            List<LeaseType> leaseTypes,
            LocalDate date
    );

    /**
     * Tenant-agnostic finder used by {@code RentChargeScheduler}: leases in
     * any of the given statuses, across all tenants. Mirrors the
     * tenant-agnostic shape already established by
     * {@code findAllByStatusAndStartDateLessThanEqual} and
     * {@code findAllByStatusInAndLeaseTypeInAndEndDateLessThanEqual} above —
     * scheduled sweeps run outside any single tenant's request scope, so
     * they query across tenants and rely on each returned {@code Lease}
     * carrying its own {@code tenantId} for downstream calls.
     */
    List<Lease> findAllByStatusIn(List<LeaseStatus> statuses);

    /**
     * Optional domain-level convenience query
     * (can be derived from active + unit filter in service if needed)
     */
    default Optional<Lease> findActiveLeaseByUnitIdAndTenantId(UUID unitId, UUID tenantId) {
        return findAllByTenant(tenantId).stream()
                .filter(l -> l.getUnitId().equals(unitId))
                .filter(l -> l.getStatus() != null && l.getStatus().name().equals("ACTIVE"))
                .findFirst();
    }

    default boolean existsActiveLeaseByUnitIdAndTenantId(UUID unitId, UUID tenantId) {
        return findActiveLeaseByUnitIdAndTenantId(unitId, tenantId).isPresent();
    }

    default List<Lease> findLeasesEndingBefore(UUID tenantId, LocalDate date) {
        return findAllByTenant(tenantId).stream()
                .filter(l -> l.getEndDate() != null && l.getEndDate().isBefore(date))
                .toList();
    }


    Optional<Lease> findByUnitIdAndStatus(UUID unitId, LeaseStatus status);

    Optional<Lease> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean hasActiveLeaseForUnit(UUID unitId);

    long countAll();

    List<Lease> findAllByIdIn(Collection<UUID> ids);

    Page<Lease> search(UUID tenantId, UUID propertyId, LeaseStatus status, LocalDate fromDate, LocalDate toDate, Pageable pageable);
}