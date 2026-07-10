package com.rentmanager.modules.rentledger.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentLedgerEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentLedgerEntryJpaRepository extends JpaRepository<RentLedgerEntryJpaEntity, UUID> {

    Optional<RentLedgerEntryJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<RentLedgerEntryJpaEntity> findAllByTenantId(UUID tenantId);

    List<RentLedgerEntryJpaEntity> findByTenantIdAndLeaseId(UUID tenantId, UUID leaseId);

    Optional<RentLedgerEntryJpaEntity> findByLeaseIdAndBillingPeriodStart(UUID leaseId, LocalDate billingPeriodStart);

    List<RentLedgerEntryJpaEntity> findByTenantIdAndStatusInAndDueDateLessThanEqual(
            UUID tenantId,
            List<RentLedgerStatus> statuses,
            LocalDate cutoffDate
    );

    List<RentLedgerEntryJpaEntity> findByTenantIdAndStatus(UUID tenantId, RentLedgerStatus status);
}