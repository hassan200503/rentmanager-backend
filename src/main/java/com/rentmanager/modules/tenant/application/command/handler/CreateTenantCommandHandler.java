package com.rentmanager.modules.tenant.application.command.handler;

import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;

/**
 * Handles Tenant creation workflow.
 */
public class CreateTenantCommandHandler {

    private final TenantRepository tenantRepository;

    public CreateTenantCommandHandler(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant handle(
            String name,
            String email,
            String phoneNumber,
            String address,
            String referenceCode,
            TenantType tenantType
    ) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tenant name must not be empty");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Tenant email must not be empty");
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Tenant phone number must not be empty");
        }

        if (referenceCode == null || referenceCode.isBlank()) {
            throw new IllegalArgumentException("Reference code must not be empty");
        }

        if (tenantType == null) {
            throw new IllegalArgumentException("Tenant type must not be null");
        }

        Tenant tenant = Tenant.create(
                name,
                email,
                phoneNumber,
                address,
                referenceCode,
                tenantType
        );

        Tenant savedTenant = tenantRepository.save(tenant);

        return savedTenant;
    }
}