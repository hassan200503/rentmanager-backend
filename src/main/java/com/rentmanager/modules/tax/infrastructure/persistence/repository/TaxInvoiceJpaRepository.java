package com.rentmanager.modules.tax.infrastructure.persistence.repository;

import com.rentmanager.modules.tax.domain.enums.TaxInvoiceStatus;
import com.rentmanager.modules.tax.infrastructure.persistence.entity.TaxInvoiceJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxInvoiceJpaRepository extends JpaRepository<TaxInvoiceJpaEntity, UUID> {

    boolean existsByTenantIdAndRentTransactionId(UUID tenantId, UUID rentTransactionId);

    Optional<TaxInvoiceJpaEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Invoices due for (re-)transmission: PENDING/FAILED with attempts
     * remaining and nextAttemptAt due. nextAttemptAt NULL (exhausted) rows
     * are excluded by the attemptCount guard.
     */
    @Query("""
            select distinct t from TaxInvoiceJpaEntity t
            where t.status in (:statuses)
              and t.attemptCount < :maxAttempts
              and (t.nextAttemptAt is null or t.nextAttemptAt <= :now)
            order by t.nextAttemptAt asc
            """)
    List<TaxInvoiceJpaEntity> findDue(
            @Param("statuses") List<TaxInvoiceStatus> statuses,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}