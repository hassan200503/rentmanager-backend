package com.rentmanager.modules.maintenance.infrastructure.persistence.repository;

import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.infrastructure.persistence.entity.MaintenanceRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ORDERING. Every list query here sorts newest-first. Without an explicit
 * ORDER BY, Postgres is free to return rows in any order, and it did: the
 * renter portal was rendering requests interleaved (1 Aug, 2 Aug, 1 Aug,
 * 3 Aug, 14 Aug) because the order happened to follow physical row layout.
 * Sorting at the source keeps every consumer — renter portal and landlord
 * views alike — consistent, rather than each caller having to re-sort.
 * The announcement repository already does this via
 * findAllByTenantIdOrderByCreatedAtDesc; this brings maintenance in line.
 */
public interface MaintenanceRequestJpaRepository extends JpaRepository<MaintenanceRequestJpaEntity, UUID> {

    Optional<MaintenanceRequestJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<MaintenanceRequestJpaEntity> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndUnitIdOrderByCreatedAtDesc(UUID tenantId, UUID unitId);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndTenantProfileIdOrderByCreatedAtDesc(
            UUID tenantId, UUID tenantProfileId);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, MaintenanceRequestStatus status);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndPriorityOrderByCreatedAtDesc(
            UUID tenantId, MaintenancePriority priority);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndPriorityAndStatusOrderByCreatedAtDesc(
            UUID tenantId, MaintenancePriority priority, MaintenanceRequestStatus status);

    long countByTenantIdAndLandlordViewedAtIsNull(UUID tenantId);

    /**
     * V54/TD-128: bulk-mark all unviewed requests for the tenant as viewed. A
     * single statement keeps the sidebar badge clearing cheap even with many
     * requests; lifecycle callbacks don't fire for bulk updates, so
     * updated_at is set explicitly.
     *
     * Both columns are now TIMESTAMPTZ (V91), so one Instant value covers both.
     */
    @Modifying
    @Query("""
            UPDATE MaintenanceRequestJpaEntity m
               SET m.landlordViewedAt = :now, m.updatedAt = :now
             WHERE m.tenantId = :tenantId AND m.landlordViewedAt IS NULL
            """)
    int markAllViewed(@Param("tenantId") UUID tenantId,
                      @Param("now") Instant now);
}
