package com.rentmanager.modules.unit.infrastructure.persistence.adapter;

import com.rentmanager.modules.unit.domain.model.UnitAmenity;
import com.rentmanager.modules.unit.domain.repository.UnitAmenityRepository;
import com.rentmanager.modules.unit.infrastructure.persistence.mapper.UnitAmenityPersistenceMapper;
import com.rentmanager.modules.unit.infrastructure.persistence.repository.UnitAmenityJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class UnitAmenityRepositoryAdapter implements UnitAmenityRepository {

    private final UnitAmenityJpaRepository jpaRepository;
    private final UnitAmenityPersistenceMapper mapper;

    // =====================================================
    // SAVE
    // =====================================================
    @Override
    public UnitAmenity save(UnitAmenity amenity) {
        return mapper.toDomain(
                jpaRepository.save(mapper.toJpaEntity(amenity))
        );
    }

    // =====================================================
    // FIND BY TENANT
    // =====================================================
    @Override
    public List<UnitAmenity> findByTenantId(UUID tenantId) {
        return jpaRepository.findByTenantId(tenantId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    // =====================================================
    // FIND BY UNIT
    // =====================================================
    @Override
    public List<UnitAmenity> findByTenantIdAndUnitId(UUID tenantId, UUID unitId) {
        return jpaRepository.findByTenantIdAndUnitId(tenantId, unitId)
                .stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    // =====================================================
    // EXISTS CHECK
    // =====================================================
    @Override
    public boolean existsByTenantIdAndUnitIdAndName(UUID tenantId, UUID unitId, String name) {
        return jpaRepository.existsByTenantIdAndUnitIdAndName(tenantId, unitId, name);
    }

    // =====================================================
    // DELETE
    // =====================================================
    @Override
    public void delete(UnitAmenity amenity) {
        jpaRepository.deleteById(amenity.getId());
    }
}