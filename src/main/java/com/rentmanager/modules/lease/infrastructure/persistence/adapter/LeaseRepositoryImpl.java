package com.rentmanager.modules.lease.infrastructure.persistence.adapter;

import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.lease.infrastructure.persistence.entity.LeaseEntity;
import com.rentmanager.modules.lease.infrastructure.persistence.mapper.LeaseMapper;
import com.rentmanager.modules.lease.infrastructure.persistence.repository.JpaLeaseRepository;
import com.rentmanager.modules.lease.infrastructure.persistence.specification.LeaseSpecification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
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

        if (lease.getId() != null) {
            entity = jpaRepository.findById(lease.getId())
                    .orElse(new LeaseEntity());
        } else {
            entity = new LeaseEntity();
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
}