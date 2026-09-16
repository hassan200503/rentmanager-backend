package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentTransactionJpaRepository extends JpaRepository<RentTransactionJpaEntity, UUID> {

    Optional<RentTransactionJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<RentTransactionJpaEntity> findByTenantIdAndLedgerEntryId(UUID tenantId, UUID ledgerEntryId);

    List<RentTransactionJpaEntity> findByTenantIdAndLeaseId(UUID tenantId, UUID leaseId);

    Optional<RentTransactionJpaEntity> findByTenantIdAndExternalReference(UUID tenantId, String externalReference);

    Optional<RentTransactionJpaEntity> findByTenantIdAndIdempotencyKey(UUID tenantId, String idempotencyKey);

    Optional<RentTransactionJpaEntity> findByTenantIdAndReversesTransactionId(UUID tenantId, UUID reversesTransactionId);

    Page<RentTransactionJpaEntity> findByTenantIdOrderByOccurredAtDesc(UUID tenantId, Pageable pageable);

    long countByTenantId(UUID tenantId);
}