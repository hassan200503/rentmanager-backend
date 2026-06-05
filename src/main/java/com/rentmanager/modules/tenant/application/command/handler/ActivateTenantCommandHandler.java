package com.rentmanager.modules.tenant.application.command.handler;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;

import java.util.UUID;

/**
 * Handles Tenant activation workflow.
 */
public class ActivateTenantCommandHandler {

    private final TenantRepository tenantRepository;

    public ActivateTenantCommandHandler(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant handle(UUID tenantId) {

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Tenant not found: " + tenantId)
                );

        tenant.activate();

        tenantRepository.save(tenant);

        return tenant;
    }
}