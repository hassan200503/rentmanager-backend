package com.rentmanager.modules.maintenance.infrastructure.persistence.repository;

import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.infrastructure.persistence.entity.MaintenanceRequestJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
