package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentLedgerEntryRepository {

    RentLedgerEntry save(RentLedgerEntry entry);

    Optional<RentLedgerEntry> findById(UUID id);

    Optional<RentLedgerEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    List<RentLedgerEntry> findAllByTenant(UUID tenantId);

    List<RentLedgerEntry> findByLease(UUID tenantId, UUID leaseId);

    /**
     * Idempotency guard for Phase 3's recurring scheduler: a (lease_id,
     * billing_period_start) uniqueness check so a re-run or overlapping
     * trigger never double-posts rent due for the same period.
     */
    Optional<RentLedgerEntry> findByLeaseIdAndBillingPeriodStart(UUID leaseId, LocalDate billingPeriodStart);

    /**
     * Used by Phase 4's overdue-detection scheduler: entries in DUE or
     * PARTIALLY_PAID whose dueDate + grace period has elapsed.
     */
    List<RentLedgerEntry> findByTenantAndStatusInAndDueDateLessThanEqual(
            UUID tenantId,
            List<RentLedgerStatus> statuses,
            LocalDate cutoffDate
    );

    List<RentLedgerEntry> findByTenantAndStatus(UUID tenantId, RentLedgerStatus status);

    void delete(UUID id);
}