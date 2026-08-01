package com.rentmanager.modules.maintenance.domain.repository;

import com.rentmanager.modules.maintenance.domain.enums.MaintenancePriority;
import com.rentmanager.modules.maintenance.domain.enums.MaintenanceRequestStatus;
import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaintenanceRequestRepository {

    MaintenanceRequest save(MaintenanceRequest request);

    Optional<MaintenanceRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    List<MaintenanceRequest> findAllByTenantId(UUID tenantId);

    List<MaintenanceRequest> findByTenantIdAndUnitId(UUID tenantId, UUID unitId);

    List<MaintenanceRequest> findByTenantIdAndTenantProfileId(UUID tenantId, UUID tenantProfileId);

    List<MaintenanceRequest> findByTenantIdAndStatus(UUID tenantId, MaintenanceRequestStatus status);

    List<MaintenanceRequest> findByTenantIdAndPriority(UUID tenantId, MaintenancePriority priority);

    List<MaintenanceRequest> findByTenantIdAndPriorityAndStatus(
            UUID tenantId, MaintenancePriority priority, MaintenanceRequestStatus status);

    void delete(UUID id);
}
