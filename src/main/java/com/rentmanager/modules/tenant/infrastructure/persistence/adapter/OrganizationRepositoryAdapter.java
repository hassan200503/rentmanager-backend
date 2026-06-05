package com.rentmanager.modules.tenant.infrastructure.persistence.adapter;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Organization is a tenant-scoped relationship in current architecture.
 *
 * RULE:
 * - Organization is NOT an aggregate root here
 * - It is stored as tenant.organizationId
 * - All operations go through Tenant aggregate
 */
@Component
public class OrganizationRepositoryAdapter {

    private final TenantRepository tenantRepository;

    public OrganizationRepositoryAdapter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    /**
     * Assign organization to tenant
     */
    public void assignOrganization(UUID tenantId, UUID organizationId) {

        Tenant tenant = findTenant(tenantId);

        tenant.assignOrganization(organizationId);

        tenantRepository.save(tenant);
    }

    /**
     * Change organization for tenant
     */
    public void changeOrganization(UUID tenantId, UUID organizationId) {

        Tenant tenant = findTenant(tenantId);

        tenant.assignOrganization(organizationId);

        tenantRepository.save(tenant);
    }

    /**
     * Remove organization from tenant
     */
    public void removeOrganization(UUID tenantId) {

        Tenant tenant = findTenant(tenantId);

        tenant.assignOrganization(null);

        tenantRepository.save(tenant);
    }

    private Tenant findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }
}