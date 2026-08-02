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

    /**
     * V54: number of requests the landlord has not seen yet (landlord_viewed_at
     * still NULL). Powers the sidebar "Requests" badge.
     */
    long countUnviewedByTenantId(UUID tenantId);

    /**
     * V54: marks every currently-unviewed request for the tenant as viewed.
     * Returns how many were updated.
     */
    int markAllViewedByTenantId(UUID tenantId);

    void delete(UUID id);
}
