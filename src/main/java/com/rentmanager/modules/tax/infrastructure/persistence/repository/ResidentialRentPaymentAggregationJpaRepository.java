package com.rentmanager.modules.tax.infrastructure.persistence.repository;

import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentLedgerEntryJpaEntity;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentTransactionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cross-module projection over the rent ledger for the MRI computation.
 *
 * <p>The PAST joins {@code rent_transactions -> rent_ledger_entries ->
 * units -> properties} and sums PAYMENT-type transactions for RESIDENTIAL
 * premises only. The residential filter lives in the query itself so the
 * MRI regime can never accidentally include COMMERCIAL rent (excluded from
 * MRI by the Finance Act 2023 wording).
 *
 * <p>The {@code occurred_at} column is used as the grouping key month
 * (document date of the transaction), matching how the rent ledger records
 * payments.
 */
public interface ResidentialRentPaymentAggregationJpaRepository
        extends JpaRepository<RentTransactionJpaEntity, UUID> {

    @Query("""
            select sum(t.amount)
            from RentTransactionJpaEntity t
            join RentLedgerEntryJpaEntity e on e.id = t.ledgerEntryId
            join com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity u on u.id = e.unitId
            join com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity p on p.id = u.propertyId
            where t.tenantId = :tenantId
              and t.type = com.rentmanager.modules.rentledger.domain.enums.RentTransactionType.PAYMENT
              and t.occurredAt >= :periodStart
              and t.occurredAt < :periodEndExclusive
              and p.premisesType = com.rentmanager.modules.property.domain.enums.PremisesType.RESIDENTIAL
            """)
    BigDecimal sumResidentialPayments(
            @Param("tenantId") UUID tenantId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEndExclusive") LocalDateTime periodEndExclusive
    );

    @Query("""
            select distinct t.tenantId
            from RentTransactionJpaEntity t
            join RentLedgerEntryJpaEntity e on e.id = t.ledgerEntryId
            join com.rentmanager.modules.unit.infrastructure.persistence.entity.UnitJpaEntity u on u.id = e.unitId
            join com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity p on p.id = u.propertyId
            where t.type = com.rentmanager.modules.rentledger.domain.enums.RentTransactionType.PAYMENT
              and t.occurredAt >= :periodStart
              and t.occurredAt < :periodEndExclusive
              and p.premisesType = com.rentmanager.modules.property.domain.enums.PremisesType.RESIDENTIAL
            """)
    List<UUID> findTenantIdsWithResidentialPayments(
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEndExclusive") LocalDateTime periodEndExclusive
    );
}