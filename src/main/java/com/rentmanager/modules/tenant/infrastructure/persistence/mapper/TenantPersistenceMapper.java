package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface TenantPersistenceMapper {

    // ============================
    // DOMAIN → JPA ENTITY (PERSISTENCE WRITE)
    // ============================
    @Mapping(target = "id", ignore = true) // JPA owns identity generation
    TenantEntity toJpaEntity(Tenant tenant);

    // ============================
    // JPA ENTITY → DOMAIN (REHYDRATION READ)
    // ============================
    default Tenant toDomain(TenantEntity entity) {

        if (entity == null) {
            return null;
        }

        return Tenant.rehydrate(
                entity.getId(),
                entity.getTenantCode(),
                entity.getName(),
                entity.getSlug(),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getType(),
                entity.getStatus(),
                entity.getSubscriptionStatus(),
                entity.getOrganizationId(),
                entity.getActiveSubscriptionId(),
                entity.getTimezone(),
                entity.getCurrency(),
                entity.getLocale(),
                entity.isActive(),
                entity.isOnboardingCompleted()
        );
    }
}