package com.rentmanager.modules.tax.infrastructure.persistence.adapter;

import com.rentmanager.modules.tax.application.port.ResidentialRentPaymentAggregationPort;
import com.rentmanager.modules.tax.infrastructure.persistence.repository.ResidentialRentPaymentAggregationJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Implements {@link ResidentialRentPaymentAggregationPort} over the
 * cross-module JPQL projection (see
 * {@link ResidentialRentPaymentAggregationJpaRepository}).
 */
@Component
@RequiredArgsConstructor
public class ResidentialRentPaymentAggregationAdapter implements ResidentialRentPaymentAggregationPort {

    private final ResidentialRentPaymentAggregationJpaRepository jpaRepository;

    @Override
    public BigDecimal sumResidentialPaymentsInPeriod(
            UUID tenantId, LocalDate periodStart, LocalDate periodEndExclusive) {
        BigDecimal sum = jpaRepository.sumResidentialPayments(
                tenantId, atStart(periodStart), atStart(periodEndExclusive));
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public List<UUID> findTenantIdsWithResidentialPaymentsInPeriod(
            LocalDate periodStart, LocalDate periodEndExclusive) {
        return jpaRepository.findTenantIdsWithResidentialPayments(
                atStart(periodStart), atStart(periodEndExclusive));
    }

    private LocalDateTime atStart(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }
}