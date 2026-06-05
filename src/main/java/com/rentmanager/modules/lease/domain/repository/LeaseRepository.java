package com.rentmanager.modules.lease.domain.repository;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
}