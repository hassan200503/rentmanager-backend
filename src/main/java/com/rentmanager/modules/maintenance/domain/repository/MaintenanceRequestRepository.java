package com.rentmanager.modules.maintenance.domain.repository;

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

    void delete(UUID id);
}
