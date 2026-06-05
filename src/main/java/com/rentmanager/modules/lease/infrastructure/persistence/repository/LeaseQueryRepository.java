package com.rentmanager.modules.lease.infrastructure.persistence.repository;

import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * OPTIONAL: Custom query repository for optimized reads
 *
 * Use ONLY when:
 * - Specification is too heavy
 * - performance-critical dashboards
 * - reporting endpoints
 */
public interface LeaseQueryRepository {

    List<LeaseEntity> findActiveLeases(UUID tenantId);

    List<LeaseEntity> findExpiredLeases(UUID tenantId);

    List<LeaseEntity> findLeasesByProperty(UUID tenantId, UUID propertyId);

    List<LeaseEntity> findLeasesEndingBefore(UUID tenantId, LocalDate date);
}