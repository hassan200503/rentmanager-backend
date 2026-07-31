package com.rentmanager.modules.tenant.infrastructure.persistence.mapper;

import com.rentmanager.modules.tenant.domain.valueobject.BrandingSettings;
import com.rentmanager.modules.tenant.domain.valueobject.DarajaCredentials;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
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

        // Same defensive fallback pattern as brandingSettings above: guards
        // against a null embeddable reference in the unlikely event
        // Hibernate ever returns one, rather than an all-null-fields
        // instance (its normal behavior for @Embedded columns that are all
        // NULL in the row).
        DarajaCredentials darajaCredentials =
                entity.getDarajaCredentials() != null
                        ? entity.getDarajaCredentials()
                        : DarajaCredentials.unconfigured();

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
                entity.getCommissionRate(),
                darajaCredentials,
                entity.getAddress(),
                entity.getPayoutPhoneNumber(),
                entity.getBillingMode(),
                entity.getSubscriptionPlanId(),
                entity.getPlanStartDate(),
                entity.getPlanEndDate(),
                entity.getPlanGraceEndsAt(),
                entity.isPlanAutoRenew()
        );
    }
}