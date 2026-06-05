package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.domain.model.SubscriptionPlan;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.SubscriptionPlanEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionPlanPersistenceMapper {

    // =========================
    // ENTITY → DOMAIN
    // =========================
    default SubscriptionPlan toDomain(SubscriptionPlanEntity entity) {
        if (entity == null) return null;

        return SubscriptionPlan.reconstruct(
                entity.getPlanCode(),
                entity.getName(),
                entity.getDescription(),
                com.rentmanager.modules.tenant.domain.enums.BillingCycle.valueOf(entity.getBillingInterval()),
                entity.getMaxProperties(),
                entity.getMaxUnits(),
                entity.getMaxUsers(),
                null, // maxStorageGb NOT IN ENTITY
                null, // monthlyPrice (not stored in entity model)
                null, // yearlyPrice (not stored in entity model)
                entity.isActive()
        );
    }

    // =========================
    // DOMAIN → ENTITY
    // =========================
    default SubscriptionPlanEntity toJpaEntity(SubscriptionPlan domain) {
        if (domain == null) return null;

        SubscriptionPlanEntity entity = new SubscriptionPlanEntity();

        entity.setPlanCode(domain.getCode());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setBillingInterval(domain.getBillingCycle().name());
        entity.setMaxProperties(domain.getMaxProperties());
        entity.setMaxUnits(domain.getMaxUnits());
        entity.setMaxUsers(domain.getMaxUsers());
        entity.setActive(domain.isActive());

        // NOTE: pricing not mapped because entity model uses single price field
        return entity;
    }
}