package com.rentmanager.modules.unit.infrastructure.persistence.adapter;

import com.rentmanager.modules.unit.domain.model.UnitMedia;
import com.rentmanager.modules.unit.domain.repository.UnitMediaRepository;
import com.rentmanager.modules.unit.infrastructure.persistence.mapper.UnitMediaPersistenceMapper;
import com.rentmanager.modules.unit.infrastructure.persistence.repository.UnitMediaJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
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
    public List<UnitMedia> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantId(tenantId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UnitMedia> findByTenantIdAndUnitId(UUID tenantId, UUID unitId) {
        return jpaRepository.findByTenantIdAndUnitId(tenantId, unitId)
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
}