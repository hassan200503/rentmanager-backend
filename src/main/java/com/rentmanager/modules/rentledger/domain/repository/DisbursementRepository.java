package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisbursementRepository {
    Disbursement save(Disbursement disbursement);
    Optional<Disbursement> findById(UUID id);
    Optional<Disbursement> findByIdAndTenantId(UUID id, UUID tenantId);
    List<Disbursement> findByStatusIn(List<DisbursementStatus> statuses);
    List<Disbursement> findByTenantId(UUID tenantId);

    /**
     * Every disbursement raised against one ledger entry, tenant-scoped.
     * Used to work out how much of that entry's net proceeds have already
     * been paid out, so a second payout cannot exceed what remains.
     */
    List<Disbursement> findByLedgerEntryId(UUID tenantId, UUID ledgerEntryId);
    List<Disbursement> findByTenantIdAndStatusIn(UUID tenantId, List<DisbursementStatus> statuses);
    List<Disbursement> findByStatusInAndRetryCountLessThan(List<DisbursementStatus> statuses, int maxRetries);
    List<Disbursement> findByStatusInAndCreatedAtBefore(List<DisbursementStatus> statuses, Instant cutoff);
}