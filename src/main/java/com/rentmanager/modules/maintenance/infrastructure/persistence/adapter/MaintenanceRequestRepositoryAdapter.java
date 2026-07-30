package com.rentmanager.modules.maintenance.infrastructure.persistence.adapter;

import com.rentmanager.modules.maintenance.domain.model.MaintenanceRequest;
import com.rentmanager.modules.maintenance.domain.repository.MaintenanceRequestRepository;
import com.rentmanager.modules.maintenance.infrastructure.persistence.entity.MaintenanceRequestJpaEntity;
import com.rentmanager.modules.maintenance.infrastructure.persistence.mapper.MaintenanceRequestPersistenceMapper;
import com.rentmanager.modules.maintenance.infrastructure.persistence.repository.MaintenanceRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MaintenanceRequestRepositoryAdapter implements MaintenanceRequestRepository {

    private final MaintenanceRequestJpaRepository jpaRepository;
    private final MaintenanceRequestPersistenceMapper mapper;

    @Override
    public MaintenanceRequest save(MaintenanceRequest request) {
        MaintenanceRequestJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(request));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<MaintenanceRequest> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public List<MaintenanceRequest> findAllByTenantId(UUID tenantId) {
        return jpaRepository.findAllByTenantId(tenantId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<MaintenanceRequest> findByTenantIdAndUnitId(UUID tenantId, UUID unitId) {
        return jpaRepository.findByTenantIdAndUnitId(tenantId, unitId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<MaintenanceRequest> findByTenantIdAndTenantProfileId(UUID tenantId, UUID tenantProfileId) {
        return jpaRepository.findByTenantIdAndTenantProfileId(tenantId, tenantProfileId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}
