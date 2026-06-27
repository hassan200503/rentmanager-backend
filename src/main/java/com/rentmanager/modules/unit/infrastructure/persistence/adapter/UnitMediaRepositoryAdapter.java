package com.rentmanager.modules.unit.infrastructure.persistence.adapter;

import com.rentmanager.modules.unit.domain.model.UnitMedia;
import com.rentmanager.modules.unit.domain.repository.UnitMediaRepository;
import com.rentmanager.modules.unit.infrastructure.persistence.mapper.UnitMediaPersistenceMapper;
import com.rentmanager.modules.unit.infrastructure.persistence.repository.UnitMediaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UnitMediaRepositoryAdapter implements UnitMediaRepository {

    private final UnitMediaJpaRepository jpaRepository;
    private final UnitMediaPersistenceMapper mapper;

    @Override
    public UnitMedia save(UnitMedia media) {
        return mapper.toDomain(
                jpaRepository.save(mapper.toJpaEntity(media))
        );
    }

    @Override
    public Optional<UnitMedia> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<UnitMedia> findByTenantIdAndUnitIdAndPrimaryMediaTrue(UUID tenantId, UUID unitId) {
        return jpaRepository.findByTenantIdAndUnitIdAndPrimaryMediaTrue(tenantId, unitId)
                .map(mapper::toDomain);
    }

    @Override
    public List<UnitMedia> findAllByTenantIdAndUnitId(UUID tenantId, UUID unitId) {
        return jpaRepository.findByTenantIdAndUnitId(tenantId, unitId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UnitMedia> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantId(tenantId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByTenantIdAndUnitIdAndUrl(UUID tenantId, UUID unitId, String url) {
        return jpaRepository.existsByTenantIdAndUnitIdAndUrl(tenantId, unitId, url);
    }

    @Override
    public void delete(UnitMedia media) {
        jpaRepository.deleteById(media.getId());
    }

    @Override
    public List<UnitMedia> findAllByUnitIdIn(List<UUID> unitIds) {
        return jpaRepository.findAllByUnitIdIn(unitIds)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UnitMedia> findAllByUnitId(UUID unitId) {
        return jpaRepository.findAllByUnitId(unitId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void clearPrimaryForUnit(UUID tenantId, UUID unitId) {
        jpaRepository.clearPrimaryForUnit(tenantId, unitId);
    }

    @Override
    public void setPrimaryById(UUID id, UUID tenantId) {
        jpaRepository.setPrimaryById(id, tenantId);
    }
}