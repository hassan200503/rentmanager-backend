package com.rentmanager.modules.tax.domain.repository;

import com.rentmanager.modules.tax.domain.model.MonthlyRentalIncomeFiling;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MonthlyRentalIncomeFilingRepository {

    Optional<MonthlyRentalIncomeFiling> findById(UUID id);

    Optional<MonthlyRentalIncomeFiling> findByTenantIdAndPeriod(UUID tenantId, LocalDate period);

    MonthlyRentalIncomeFiling save(MonthlyRentalIncomeFiling filing);
}
