package com.rentmanager.modules.rentledger.domain.repository;

import com.rentmanager.modules.rentledger.domain.model.RentTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RentTransactionRepository {

    RentTransaction save(RentTransaction transaction);

    Optional<RentTransaction> findById(UUID id);

    Optional<RentTransaction> findByIdAndTenantId(UUID id, UUID tenantId);

    List<RentTransaction> findByLedgerEntry(UUID tenantId, UUID ledgerEntryId);

    List<RentTransaction> findByLease(UUID tenantId, UUID leaseId);

    /**
     * Idempotency check for M-Pesa callback replay — mirrors the guard
     * MpesaCallbackService already relies on for PaymentIntent.
     */
    Optional<RentTransaction> findByExternalReference(UUID tenantId, String externalReference);

    /**
     * Idempotency check before posting a REVERSAL — at most one reversal per
     * original transaction, backstopped by
     * {@code uk_rent_transactions_reverses_transaction_id}.
     */
    Optional<RentTransaction> findByReversesTransactionId(UUID tenantId, UUID transactionId);

    List<RentTransaction> findAllByTenant(UUID tenantId);

    long countByTenantId(UUID tenantId);
}