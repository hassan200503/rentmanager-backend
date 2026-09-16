package com.rentmanager.modules.tax.domain.repository;

import com.rentmanager.modules.tax.domain.model.TaxInvoice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxInvoiceRepository {

    Optional<TaxInvoice> findById(UUID id);

    Optional<TaxInvoice> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndRentTransactionId(UUID tenantId, UUID rentTransactionId);

    /**
     * Invoices ready for transmission: PENDING or FAILED, with attempts
     * remaining and nextAttemptAt due.
     */
    List<TaxInvoice> findDue(LocalDateTime now, int maxAttempts, int limit);

    List<TaxInvoice> findAllByTenantId(UUID tenantId, int page, int size);

    long countAttentionRequiredByTenantId(UUID tenantId);

    TaxInvoice save(TaxInvoice invoice);
}
