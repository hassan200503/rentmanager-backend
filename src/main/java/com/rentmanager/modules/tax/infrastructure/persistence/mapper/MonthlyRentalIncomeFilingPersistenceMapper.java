package com.rentmanager.modules.tax.infrastructure.persistence.mapper;

import com.rentmanager.modules.tax.domain.model.MonthlyRentalIncomeFiling;
import com.rentmanager.modules.tax.infrastructure.persistence.entity.MonthlyRentalIncomeFilingJpaEntity;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;

@Component
public class MonthlyRentalIncomeFilingPersistenceMapper {

    public MonthlyRentalIncomeFilingJpaEntity toJpaEntity(MonthlyRentalIncomeFiling filing) {
        if (filing == null) {
            return null;
        }

        MonthlyRentalIncomeFilingJpaEntity entity = new MonthlyRentalIncomeFilingJpaEntity();
        entity.assignTenantIfUnset(filing.getTenantId());
        entity.setPeriod(filing.getPeriod());
        entity.setGrossRentalIncome(filing.getGrossRentalIncome());
        entity.setNilReturn(filing.isNilReturn());
        entity.setMriRateApplied(filing.getMriRateApplied());
        entity.setMriTaxDue(filing.getMriTaxDue());
        entity.setStatus(filing.getStatus());
        entity.setComputedAt(filing.getComputedAt());
        entity.setFiledAt(filing.getFiledAt());
        entity.setTransmittedAt(filing.getTransmittedAt());
        entity.setAttemptCount(filing.getAttemptCount());
        entity.setNextAttemptAt(filing.getNextAttemptAt());
        entity.setLastError(filing.getLastError());

        setField(entity, "id", filing.getId());
        setField(entity, "createdAt", filing.getCreatedAt());
        setField(entity, "updatedAt", filing.getUpdatedAt());
        setField(entity, "version", filing.getVersion());
        return entity;
    }

    public MonthlyRentalIncomeFiling toDomain(MonthlyRentalIncomeFilingJpaEntity entity) {
        if (entity == null) {
            return null;
        }

        return MonthlyRentalIncomeFiling.rehydrate(
                entity.getId(),
                entity.getTenantId(),
                entity.getPeriod(),
                entity.getGrossRentalIncome(),
                entity.isNilReturn(),
                entity.getMriRateApplied(),
                entity.getMriTaxDue(),
                entity.getStatus(),
                entity.getComputedAt(),
                entity.getFiledAt(),
                entity.getTransmittedAt(),
                entity.getAttemptCount(),
                entity.getNextAttemptAt(),
                entity.getLastError(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null) {
                try {
                    Field field = clazz.getDeclaredField(fieldName);
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (NoSuchFieldException ignored) {
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to map field: " + fieldName, ex);
        }
    }
}