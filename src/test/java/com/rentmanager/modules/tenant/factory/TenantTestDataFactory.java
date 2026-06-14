package com.rentmanager.modules.tenant.factory;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantTestDataFactory {

    private final TenantRepository tenantRepository;

    public TenantTestDataFactory(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    private String id(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public Tenant createTenant(
            String name,
            String slug,
            String email,
            TenantType type
    ) {
        Tenant tenant = Tenant.create(
                id("T"),
                name,
                id(slug),
                id(email),
                "0700000000",
                type
        );

        return tenantRepository.save(tenant);
    }

    public Tenant createTenantWithSubscription(
            String name,
            String slug,
            String email,
            TenantType type,
            SubscriptionStatus status
    ) {
        Tenant tenant = Tenant.create(
                id("T"),
                name,
                id(slug),
                id(email),
                "0700000000",
                type,
                status
        );

        return tenantRepository.save(tenant);
    }
}