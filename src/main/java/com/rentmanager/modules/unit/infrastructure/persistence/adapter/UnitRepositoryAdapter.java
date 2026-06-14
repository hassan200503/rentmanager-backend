package com.rentmanager.modules.unit.infrastructure.persistence.adapter;

import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import com.rentmanager.modules.unit.infrastructure.persistence.mapper.UnitPersistenceMapper;
import com.rentmanager.modules.unit.infrastructure.persistence.repository.UnitJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UnitRepositoryAdapter implements UnitRepository {

    private final UnitJpaRepository jpaRepository;
    private final UnitPersistenceMapper mapper;

    // =====================================================
    // FIND BY ID + TENANT
    // =====================================================
    @Override
    public Optional<Unit> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId)
                .map(mapper::toDomain);
    }

    // =====================================================
    // FIND ALL BY TENANT
    // =====================================================
    @Override
    public Page<Unit> findAllByTenantId(UUID tenantId, Pageable pageable) {
        return jpaRepository.findAllByTenantId(tenantId, pageable)
                .map(mapper::toDomain);
    }

    // =====================================================
    // EXISTS CHECK
    // =====================================================
    @Override
    public boolean existsByTenantIdAndUnitNumber(UUID tenantId, String unitNumber) {
        return jpaRepository.existsByTenantIdAndUnitNumber(tenantId, unitNumber);
    }

    // =====================================================
    // FIND BY PROPERTY
    // =====================================================
    @Override
    public Page<Unit> findByTenantIdAndPropertyId(UUID tenantId, UUID propertyId, Pageable pageable) {
        return jpaRepository.findByTenantIdAndPropertyId(tenantId, propertyId, pageable)
                .map(mapper::toDomain);
    }

    // =====================================================
    // FIND BY STATUS
    // =====================================================
    @Override
    public Page<Unit> findByTenantIdAndStatus(UUID tenantId, UnitStatus status, Pageable pageable) {
        return jpaRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                .map(mapper::toDomain);
    }

    // =====================================================
    // SEARCH
    // =====================================================
    @Override
    public Page<Unit> search(UUID tenantId, String keyword, Pageable pageable) {
        return jpaRepository.search(tenantId, keyword, pageable)
                .map(mapper::toDomain);
    }

    // =====================================================
    // SAVE
    // =====================================================
    @Override
    public Unit save(Unit unit) {

        UnitJpaEntity entity = mapper.toJpaEntity(unit);

        UnitJpaEntity saved = jpaRepository.saveAndFlush(entity);

        return mapper.toDomain(saved);
    }

    // =====================================================
    // DELETE
    // =====================================================
    @Override
    public void delete(Unit unit) {
        jpaRepository.deleteById(unit.getId());
    }
}