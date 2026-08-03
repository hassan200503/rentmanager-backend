package com.rentmanager.modules.tax.infrastructure.persistence.adapter;

import com.rentmanager.modules.tax.domain.model.MonthlyRentalIncomeFiling;
import com.rentmanager.modules.tax.domain.repository.MonthlyRentalIncomeFilingRepository;
import com.rentmanager.modules.tax.infrastructure.persistence.mapper.MonthlyRentalIncomeFilingPersistenceMapper;
import com.rentmanager.modules.tax.infrastructure.persistence.repository.MonthlyRentalIncomeFilingJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MonthlyRentalIncomeFilingRepositoryAdapter implements MonthlyRentalIncomeFilingRepository {

    private final MonthlyRentalIncomeFilingJpaRepository jpaRepository;
    private final MonthlyRentalIncomeFilingPersistenceMapper mapper;

    @Override
    public Optional<MonthlyRentalIncomeFiling> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<MonthlyRentalIncomeFiling> findByTenantIdAndPeriod(UUID tenantId, LocalDate period) {
        return jpaRepository.findByTenantIdAndPeriod(tenantId, period).map(mapper::toDomain);
    }

    @Override
    public MonthlyRentalIncomeFiling save(MonthlyRentalIncomeFiling filing) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpaEntity(filing)));
    }
}