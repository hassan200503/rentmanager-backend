package com.rentmanager.modules.unit.infrastructure.persistence.adapter;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitOccupancyStatus;
import com.rentmanager.modules.unit.domain.enums.UnitStatus;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.unit.domain.repository.UnitRepository;
import com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity;
import com.rentmanager.modules.unit.infrastructure.persistence.mapper.UnitPersistenceMapper;
import com.rentmanager.modules.unit.infrastructure.persistence.repository.UnitJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @Override
    public Page<Unit> findByOccupancyStatus(
            UnitOccupancyStatus occupancyStatus,
            Pageable pageable
    ) {
        return jpaRepository
                .findByOccupancyStatus(occupancyStatus, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public Page<Unit> searchPublic(
            String keyword,
            UnitOccupancyStatus occupancyStatus,
            Pageable pageable
    ) {
        return jpaRepository
                .searchPublic(keyword, occupancyStatus, pageable)
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Unit> findById(UUID id) {
        return jpaRepository
                .findById(id)
                .map(mapper::toDomain);
    }

    // =====================================================
    // FIND BY ID FOR UPDATE (PESSIMISTIC LOCK — RESERVATION FLOW)
    // =====================================================
    @Override
    public Optional<Unit> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id)
                .map(mapper::toDomain);
    }

    @Override
    public Page<Unit> findByPropertyIdAndOccupancyStatus(
            UUID propertyId,
            UnitOccupancyStatus occupancyStatus,
            Pageable pageable
    ) {
        return jpaRepository
                .findByPropertyIdAndOccupancyStatus(
                        propertyId,
                        occupancyStatus,
                        pageable
                )
                .map(mapper::toDomain);
    }

    @Override
    public long countByTenantId(UUID tenantId) {
        return jpaRepository.countByTenantId(tenantId);
    }

    @Override
    public long countByTenantIdAndOccupancyStatus(UUID tenantId, UnitOccupancyStatus occupancyStatus) {
        return jpaRepository.countByTenantIdAndOccupancyStatus(tenantId, occupancyStatus);
    }

    @Override
    public Optional<Unit> findLongestVacant() {
        Page<UnitJpaEntity> page = jpaRepository.findLongestVacant(
                UnitOccupancyStatus.VACANT,
                PageRequest.of(0, 1)
        );
        return page.getContent().stream().findFirst().map(mapper::toDomain);
    }

    // =====================================================
    // PUBLIC LISTING HARDENING (2026-07-08)
    // See UnitJpaRepository for the join-query rationale and the
    // defense-in-depth visibility rule (unit ACTIVE + VACANT AND parent
    // property ACTIVE).
    // =====================================================

    @Override
    public Page<Unit> findPubliclyVisibleVacantUnits(String keyword, Pageable pageable) {
        return jpaRepository.searchPubliclyVisible(
                        keyword,
                        UnitOccupancyStatus.VACANT,
                        UnitStatus.ACTIVE,
                        PropertyStatus.ACTIVE,
                        pageable
                )
                .map(mapper::toDomain);
    }

    @Override
    public Page<Unit> findPubliclyVisibleVacantUnitsByProperty(UUID propertyId, Pageable pageable) {
        return jpaRepository.findPubliclyVisibleByProperty(
                        propertyId,
                        UnitOccupancyStatus.VACANT,
                        UnitStatus.ACTIVE,
                        PropertyStatus.ACTIVE,
                        pageable
                )
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Unit> findPubliclyVisibleVacantUnitById(UUID unitId) {
        return jpaRepository.findPubliclyVisibleById(
                        unitId,
                        UnitOccupancyStatus.VACANT,
                        UnitStatus.ACTIVE,
                        PropertyStatus.ACTIVE
                )
                .map(mapper::toDomain);
    }

    @Override
    public Optional<Unit> findPubliclyVisibleLongestVacantUnit() {
        Page<UnitJpaEntity> page = jpaRepository.findLongestVacantPubliclyVisible(
                UnitOccupancyStatus.VACANT,
                UnitStatus.ACTIVE,
                PropertyStatus.ACTIVE,
                PageRequest.of(0, 1)
        );
        return page.getContent().stream().findFirst().map(mapper::toDomain);
    }
}