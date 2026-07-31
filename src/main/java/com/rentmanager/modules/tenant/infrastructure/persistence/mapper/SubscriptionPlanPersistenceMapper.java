package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;



import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPlanEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionPlanPersistenceMapper {

    // =========================
    // ENTITY -> DOMAIN
    // =========================
    default SubscriptionPlan toDomain(SubscriptionPlanEntity entity) {
        if (entity == null) return null;

        return SubscriptionPlan.rehydrate(
                entity.getId(),
                entity.getVersion(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getBillingCycle(),
                null, // maxProperties NOT persisted (unit-band tiers only)
                entity.getMaxUnits(),
                null, // maxUsers NOT persisted
                null, // maxStorageGb NOT persisted
                entity.getMonthlyPrice(),
                entity.getYearlyPrice(),
                entity.isActive(),
                entity.isSelfService()
        );
    }

    // =========================
    // DOMAIN -> ENTITY
    // =========================
    default SubscriptionPlanEntity toJpaEntity(SubscriptionPlan domain) {
        if (domain == null) return null;

        SubscriptionPlanEntity entity = new SubscriptionPlanEntity();

        entity.setId(domain.getId());
        entity.setVersion(domain.getVersion());
        entity.setCode(domain.getCode());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setBillingCycle(domain.getBillingCycle());
        entity.setMaxUnits(domain.getMaxUnits());
        entity.setMonthlyPrice(domain.getMonthlyPrice());
        entity.setYearlyPrice(domain.getYearlyPrice());
        entity.setActive(domain.isActive());
        entity.setSelfService(domain.isSelfService());

        return entity;
    }
}
