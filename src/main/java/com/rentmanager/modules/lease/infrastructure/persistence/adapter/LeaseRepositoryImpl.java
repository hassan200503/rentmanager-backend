package com.rentmanager.modules.lease.infrastructure.persistence.adapter;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import com.rentmanager.modules.lease.infrastructure.persistence.mapper.LeaseMapper;
import com.rentmanager.modules.lease.infrastructure.persistence.repository.JpaLeaseRepository;
import com.rentmanager.modules.lease.infrastructure.persistence.specification.LeaseSpecification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.UUID;

/**
 * SaaS-grade persistence adapter (Anti-Corruption Layer)
 */
@Repository
public class LeaseRepositoryImpl implements LeaseRepository {

    private final JpaLeaseRepository jpaRepository;
    private final LeaseMapper mapper;

    public LeaseRepositoryImpl(JpaLeaseRepository jpaRepository,
                               LeaseMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }




    @Override
    @Transactional
    public Lease save(Lease lease) {
        LeaseEntity entity;

        if (lease.getId() != null && jpaRepository.existsById(lease.getId())) {
            entity = jpaRepository.findById(lease.getId())
                    .orElse(new LeaseEntity());
        } else {
            entity = new LeaseEntity();
            entity.setVersion(0L);
        }

        mapper.updateEntity(entity, lease);

        LeaseEntity saved = jpaRepository.save(entity);

        return mapper.toDomain(saved);
    }


    @Override
    public Optional<Lease> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lease> findAllByTenant(UUID tenantId) {
        return jpaRepository.findAll(
                        LeaseSpecification.hasTenant(tenantId)
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lease> findAllByTenantAndTenantProfile(UUID tenantId, UUID tenantProfileId) {
        return jpaRepository.findAll(
                        LeaseSpecification.hasTenant(tenantId)
                                .and(LeaseSpecification.hasTenantProfile(tenantProfileId)),
                        Sort.by(Sort.Direction.DESC, "startDate")
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lease> findActiveByTenant(UUID tenantId) {
        return jpaRepository.findAll(
                        LeaseSpecification.baseFilter(tenantId)
                                .and(LeaseSpecification.isActive())
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lease> findByProperty(UUID tenantId, UUID propertyId) {
        return jpaRepository.findAll(
                        LeaseSpecification.baseFilter(tenantId)
                                .and(LeaseSpecification.hasProperty(propertyId))
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public Optional<Lease> findByUnitIdAndStatus(UUID unitId, LeaseStatus status) {
        return jpaRepository.findByUnitIdAndStatus(unitId, status)
                .map(mapper::toDomain);
    }


    public Optional<Lease> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findById(id)
                .filter(e -> e.getTenantId().equals(tenantId))
                .map(mapper::toDomain);
    }


    @Override
    public boolean hasActiveLeaseForUnit(UUID unitId) {
        return jpaRepository.existsByUnitIdAndStatus(unitId, LeaseStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Lease> search(UUID tenantId, UUID propertyId, LeaseStatus status, LocalDate fromDate, LocalDate toDate, Pageable pageable) {
        Specification<LeaseEntity> spec = Specification.where(LeaseSpecification.hasTenant(tenantId));
        if (propertyId != null) spec = spec.and(LeaseSpecification.hasProperty(propertyId));
        if (status != null) spec = spec.and(LeaseSpecification.hasStatus(status.name()));
        if (fromDate != null) spec = spec.and(LeaseSpecification.startsAfter(fromDate));
        if (toDate != null) spec = spec.and(LeaseSpecification.endsBefore(toDate));
        return jpaRepository.findAll(spec, pageable).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return jpaRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lease> findAllByStatusAndStartDateLessThanEqual(
            LeaseStatus status,
            LocalDate date
    ) {
        return jpaRepository
                .findAllByStatusAndStartDateLessThanEqual(status, date)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lease> findAllByStatusInAndLeaseTypeInAndEndDateLessThanEqual(
            List<LeaseStatus> statuses,
            List<LeaseType> leaseTypes,
            LocalDate date
    ) {
        return jpaRepository
                .findAllByStatusInAndLeaseTypeInAndEndDateLessThanEqual(statuses, leaseTypes, date)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lease> findAllByIdIn(Collection<UUID> ids) {
        return jpaRepository.findAllById(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Lease> findAllByStatusIn(List<LeaseStatus> statuses) {
        return jpaRepository
                .findAllByStatusIn(statuses)
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

}