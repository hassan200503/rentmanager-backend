package com.rentmanager.modules.tenant.renter.infrastructure.persistence.adapter;

import com.rentmanager.modules.lease.application.port.out.TenantProfileQueryPort;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository.TenantProfileJpaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantProfileQueryAdapter implements TenantProfileQueryPort {

    private final TenantProfileJpaRepository tenantProfileJpaRepository;

    public TenantProfileQueryAdapter(TenantProfileJpaRepository tenantProfileJpaRepository) {
        this.tenantProfileJpaRepository = tenantProfileJpaRepository;
    }

    @Override
    public boolean existsById(UUID tenantProfileId) {
        return tenantProfileJpaRepository.existsById(tenantProfileId);
    }
}