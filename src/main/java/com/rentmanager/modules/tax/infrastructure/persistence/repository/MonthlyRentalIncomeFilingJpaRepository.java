package com.rentmanager.modules.tax.infrastructure.persistence.repository;

import com.rentmanager.modules.tax.infrastructure.persistence.entity.MonthlyRentalIncomeFilingJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyRentalIncomeFilingJpaRepository
        extends JpaRepository<MonthlyRentalIncomeFilingJpaEntity, UUID> {

    Optional<MonthlyRentalIncomeFilingJpaEntity> findByTenantIdAndPeriod(UUID tenantId, LocalDate period);

    Page<MonthlyRentalIncomeFilingJpaEntity> findAllByTenantIdOrderByPeriodDesc(
            UUID tenantId, Pageable pageable);

    Optional<MonthlyRentalIncomeFilingJpaEntity> findTopByTenantIdOrderByPeriodDesc(UUID tenantId);
}
