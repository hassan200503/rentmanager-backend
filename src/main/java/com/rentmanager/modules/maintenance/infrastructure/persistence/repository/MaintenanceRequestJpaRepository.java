package com.rentmanager.modules.maintenance.infrastructure.persistence.repository;

import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.infrastructure.persistence.entity.MaintenanceRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceRequestJpaRepository extends JpaRepository<MaintenanceRequestJpaEntity, UUID> {

    Optional<MaintenanceRequestJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<MaintenanceRequestJpaEntity> findAllByTenantId(UUID tenantId);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndTenantProfileId(UUID tenantId, UUID tenantProfileId);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndStatus(UUID tenantId, MaintenanceRequestStatus status);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndPriority(UUID tenantId, MaintenancePriority priority);

    List<MaintenanceRequestJpaEntity> findByTenantIdAndPriorityAndStatus(
            UUID tenantId, MaintenancePriority priority, MaintenanceRequestStatus status);

    long countByTenantIdAndLandlordViewedAtIsNull(UUID tenantId);

    /**
     * V54: bulk-mark all unviewed requests for the tenant as viewed. A
     * single statement keeps the sidebar badge clearing cheap even with many
     * requests; lifecycle callbacks don't fire for bulk updates, so
     * updated_at is set explicitly.
     */
    @Modifying
    @Query("""
            UPDATE MaintenanceRequestJpaEntity m
               SET m.landlordViewedAt = :viewedAt, m.updatedAt = :updatedAt
             WHERE m.tenantId = :tenantId AND m.landlordViewedAt IS NULL
            """)
    int markAllViewed(@Param("tenantId") UUID tenantId,
                      @Param("viewedAt") LocalDateTime viewedAt,
                      @Param("updatedAt") LocalDateTime updatedAt);
}
