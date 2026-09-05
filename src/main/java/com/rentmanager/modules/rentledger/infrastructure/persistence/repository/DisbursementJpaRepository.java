package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.DisbursementJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DisbursementJpaRepository extends JpaRepository<DisbursementJpaEntity, UUID> {
    List<DisbursementJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<DisbursementJpaEntity> findByTenantIdAndLedgerEntryId(UUID tenantId, UUID ledgerEntryId);
    List<DisbursementJpaEntity> findByStatusInOrderByCreatedAtAsc(List<DisbursementStatus> statuses);
    List<DisbursementJpaEntity> findByTenantIdAndStatusInOrderByCreatedAtDesc(UUID tenantId, List<DisbursementStatus> statuses);
    List<DisbursementJpaEntity> findByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(List<DisbursementStatus> statuses, int retryCount);
    List<DisbursementJpaEntity> findByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(List<DisbursementStatus> statuses, Instant cutoff);
}