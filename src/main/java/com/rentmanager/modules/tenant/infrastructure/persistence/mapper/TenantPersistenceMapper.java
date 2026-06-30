package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface TenantPersistenceMapper {

    // ============================
    // DOMAIN → JPA ENTITY
    // ============================
    TenantEntity toJpaEntity(Tenant tenant);

    // ============================
    // JPA ENTITY → DOMAIN
    // ============================
    default Tenant toDomain(TenantEntity entity) {

        if (entity == null) {
            return null;
        }

        BrandingSettings brandingSettings =
                entity.getBrandingSettings() != null
                        ? entity.getBrandingSettings()
                        : BrandingSettings.defaultSettings();

        return Tenant.rehydrate(
                entity.getId(),
                entity.getVersion(),
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
                brandingSettings,
                entity.isActive(),
                entity.isOnboardingCompleted(),
                entity.getClerkOrgId(),
                entity.getCommissionRate()
        );
    }
}