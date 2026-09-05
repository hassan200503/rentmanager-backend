package com.rentmanager.modules.rentledger.infrastructure.persistence.adapter;

import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.rentledger.domain.repository.RentLedgerEntryRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentLedgerEntryJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.mapper.RentLedgerEntryPersistenceMapper;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentLedgerEntryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RentLedgerEntryRepositoryAdapter implements RentLedgerEntryRepository {

    private final RentLedgerEntryJpaRepository jpaRepository;
    private final RentLedgerEntryPersistenceMapper mapper;

    @Override
    public RentLedgerEntry save(RentLedgerEntry entry) {
        RentLedgerEntryJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(entry));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RentLedgerEntry> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<RentLedgerEntry> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public Optional<RentLedgerEntry> findByIdAndTenantIdForUpdate(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantIdForUpdate(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public List<RentLedgerEntry> findAllByTenant(UUID tenantId) {
        return jpaRepository.findAllByTenantId(tenantId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RentLedgerEntry> findAllByTenantAndIdIn(UUID tenantId, List<UUID> ids) {
        // An IN () with no values is a syntax error on Postgres.
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByTenantIdAndIdIn(tenantId, ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RentLedgerEntry> findByLease(UUID tenantId, UUID leaseId) {
        return jpaRepository.findByTenantIdAndLeaseId(tenantId, leaseId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<RentLedgerEntry> findByLeaseIdAndBillingPeriodStart(UUID leaseId, LocalDate billingPeriodStart) {
        return jpaRepository.findByLeaseIdAndBillingPeriodStart(leaseId, billingPeriodStart).map(mapper::toDomain);
    }

    @Override
    public List<RentLedgerEntry> findByTenantAndStatusInAndDueDateLessThanEqual(
            UUID tenantId, List<RentLedgerStatus> statuses, LocalDate cutoffDate) {
        return jpaRepository.findByTenantIdAndStatusInAndDueDateLessThanEqual(tenantId, statuses, cutoffDate)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RentLedgerEntry> findByTenantAndStatus(UUID tenantId, RentLedgerStatus status) {
        return jpaRepository.findByTenantIdAndStatus(tenantId, status).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<RentLedgerEntry> findLatestByLeaseId(UUID leaseId) {
        return jpaRepository.findFirstByLeaseIdOrderByBillingPeriodStartDesc(leaseId).map(mapper::toDomain);
    }

    @Override
    public List<RentLedgerEntry> findAllByStatusInAndDueDateLessThanEqual(
            List<RentLedgerStatus> statuses, LocalDate cutoffDate) {
        return jpaRepository.findAllByStatusInAndDueDateLessThanEqual(statuses, cutoffDate)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RentLedgerEntry> findAllByStatusInAndDueDateBetween(
            List<RentLedgerStatus> statuses, LocalDate fromInclusive, LocalDate toInclusive) {
        return jpaRepository
                .findAllByStatusInAndDueDateBetween(statuses, fromInclusive, toInclusive)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RentLedgerEntry> findAllByUpdatedAtAfter(Instant threshold) {
        return jpaRepository.findAllByUpdatedAtAfter(threshold).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}