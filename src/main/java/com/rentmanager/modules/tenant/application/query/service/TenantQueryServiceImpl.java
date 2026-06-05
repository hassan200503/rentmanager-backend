package com.rentmanager.modules.tenant.application.query.service;

import com.rentmanager.modules.tenant.application.query.projection.TenantListProjection;
import com.rentmanager.modules.tenant.application.query.projection.TenantProjection;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TenantQueryServiceImpl implements TenantQueryService {

    private final TenantRepository tenantRepository;

    public TenantQueryServiceImpl(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public TenantProjection getTenant(UUID tenantId, UUID targetTenantId) {

        Tenant tenant = findTenant(targetTenantId);

        return new TenantProjection(
                getTenantId(tenant),
                tenant.getTenantCode(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getEmail(),
                tenant.getPhoneNumber(),
                tenant.getStatus(),
                tenant.getType(),
                tenant.getSubscriptionStatus(),
                tenant.isActive(),
                tenant.isOnboardingCompleted()
        );
    }

    @Override
    public List<TenantListProjection> getAllTenants(UUID tenantId) {

        return tenantRepository.findAll()
                .stream()
                .map(t -> new TenantListProjection(
                        getTenantId(t),
                        t.getTenantCode(),
                        t.getName(),
                        t.getSlug(),
                        t.getStatus().name()
                ))
                .collect(Collectors.toList());
    }

    @Override
    public List<TenantListProjection> searchTenants(UUID tenantId, String keyword) {

        return tenantRepository
                .findByNameContainingIgnoreCaseOrTenantCodeContainingIgnoreCase(keyword, keyword)
                .stream()
                .map(t -> new TenantListProjection(
                        getTenantId(t),
                        t.getTenantCode(),
                        t.getName(),
                        t.getSlug(),
                        t.getStatus().name()
                ))
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------
    // SAFE ID ACCESS WRAPPER (NO DOMAIN CHANGE REQUIRED)
    // ------------------------------------------------------------
    private UUID getTenantId(Tenant tenant) {
        try {
            return tenant.getId();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "BaseEntity must expose getId() for SaaS projection layer"
            );
        }
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }
}