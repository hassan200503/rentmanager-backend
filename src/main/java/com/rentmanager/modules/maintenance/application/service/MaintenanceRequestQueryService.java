package com.rentmanager.modules.maintenance.application.service;

import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceRequestQueryService {

    private final MaintenanceRequestRepository maintenanceRequestRepository;

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> findAllByTenantId(UUID tenantId) {
        return maintenanceRequestRepository.findAllByTenantId(tenantId);
    }

    @Transactional(readOnly = true)
    public MaintenanceRequest findByIdAndTenantId(UUID id, UUID tenantId) {
        return maintenanceRequestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Maintenance request not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> findByTenantIdAndUnitId(UUID tenantId, UUID unitId) {
        return maintenanceRequestRepository.findByTenantIdAndUnitId(tenantId, unitId);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceRequest> findByTenantIdAndTenantProfileId(UUID tenantId, UUID tenantProfileId) {
        return maintenanceRequestRepository.findByTenantIdAndTenantProfileId(tenantId, tenantProfileId);
    }
}
