package com.rentmanager.modules.rentledger.infrastructure.persistence.adapter;

import com.rentmanager.modules.rentledger.domain.model.RentTransaction;
import com.rentmanager.modules.rentledger.domain.repository.RentTransactionRepository;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.mapper.RentTransactionPersistenceMapper;
import com.rentmanager.modules.rentledger.infrastructure.persistence.repository.RentTransactionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RentTransactionRepositoryAdapter implements RentTransactionRepository {

    private final RentTransactionJpaRepository jpaRepository;
    private final RentTransactionPersistenceMapper mapper;

    @Override
    public RentTransaction save(RentTransaction transaction) {
        RentTransactionJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(transaction));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<RentTransaction> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<RentTransaction> findByIdAndTenantId(UUID id, UUID tenantId) {
        return jpaRepository.findByIdAndTenantId(id, tenantId).map(mapper::toDomain);
    }

    @Override
    public List<RentTransaction> findByLedgerEntry(UUID tenantId, UUID ledgerEntryId) {
        return jpaRepository.findByTenantIdAndLedgerEntryId(tenantId, ledgerEntryId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<RentTransaction> findByLease(UUID tenantId, UUID leaseId) {
        return jpaRepository.findByTenantIdAndLeaseId(tenantId, leaseId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<RentTransaction> findByExternalReference(UUID tenantId, String externalReference) {
        return jpaRepository.findByTenantIdAndExternalReference(tenantId, externalReference).map(mapper::toDomain);
    }

    @Override
    public Optional<RentTransaction> findByReversesTransactionId(UUID tenantId, UUID transactionId) {
        return jpaRepository.findByTenantIdAndReversesTransactionId(tenantId, transactionId).map(mapper::toDomain);
    }

    @Override
    public List<RentTransaction> findAllByTenant(UUID tenantId) {
        return jpaRepository.findByTenantIdOrderByOccurredAtDesc(tenantId, Pageable.unpaged()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByTenantId(UUID tenantId) {
        return jpaRepository.countByTenantId(tenantId);
    }
}