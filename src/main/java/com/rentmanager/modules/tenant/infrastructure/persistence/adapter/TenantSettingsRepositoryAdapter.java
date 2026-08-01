package com.rentmanager.modules.tenant.infrastructure.persistence.adapter;


import com.rentmanager.modules.tenant.domain.valueobject.TenantSettings;

import com.rentmanager.modules.tenant.infrastructure.persistence.mapper.TenantPersistenceMapper;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.infrastructure.persistence.entity.TenantEntity;
import com.rentmanager.modules.tenant.infrastructure.persistence.repository.TenantJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * TenantSettings is NOT an aggregate root.
 *
 * RULE:
 * - It is persisted ONLY through TenantEntity
 * - No separate repository is allowed
 * - Updates must go through Tenant aggregate
 */
@Component
public class TenantSettingsRepositoryAdapter {

    private final TenantJpaRepository jpaRepository;
    private final TenantPersistenceMapper mapper;

    public TenantSettingsRepositoryAdapter(
            TenantJpaRepository jpaRepository,
            TenantPersistenceMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Update tenant settings inside Tenant aggregate
     */
    public void updateSettings(UUID tenantId, TenantSettings settings) {

        TenantEntity entity = jpaRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        Tenant tenant = mapper.toDomain(entity);

        tenant.updateBranding(settings.getBrandingSettings());
        tenant.updateLocalization(
                settings.getTimezone(),
                settings.getCurrency(),
                settings.getLocale()
        );
        tenant.updateEmergencyContact(
                settings.getEmergencyContactPhone(),
                settings.isEmergencyContact24h()
        );

        jpaRepository.save(mapper.toJpaEntity(tenant));
    }

    /**
     * Extract settings from tenant
     */
    public TenantSettings getSettings(UUID tenantId) {

        TenantEntity entity = jpaRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        Tenant tenant = mapper.toDomain(entity);

        return TenantSettings.builder()
                .brandingSettings(tenant.getBrandingSettings())
                .timezone(tenant.getTimezone())
                .currency(tenant.getCurrency())
                .locale(tenant.getLocale())
                .emergencyContactPhone(tenant.getEmergencyContactPhone())
                .emergencyContact24h(tenant.isEmergencyContact24h())
                .build();
    }
}
