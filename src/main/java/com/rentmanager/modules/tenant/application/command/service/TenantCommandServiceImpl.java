package com.rentmanager.modules.tenant.application.command.service;

import com.rentmanager.modules.tenant.application.dto.request.*;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import com.rentmanager.modules.tenant.application.mapper.TenantMapper;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantCommandServiceImpl implements TenantCommandService {

    private final TenantRepository tenantRepository;
    private final TenantMapper tenantMapper;

    public TenantCommandServiceImpl(
            TenantRepository tenantRepository,
            TenantMapper tenantMapper
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
    }

    // ------------------------------------------------------------
    // CREATE TENANT
    // ------------------------------------------------------------
    @Override
    public TenantResponse createTenant(UUID tenantId, CreateTenantRequest request) {

        Tenant tenant;

        if (request.getSubscriptionStatus() != null) {
            tenant = Tenant.create(
                    request.getTenantCode(),
                    request.getName(),
                    request.getSlug(),
                    request.getEmail(),
                    request.getPhoneNumber(),
                    request.getTenantType(),
                    request.getSubscriptionStatus()
            );
        } else {
            tenant = Tenant.create(
                    request.getTenantCode(),
                    request.getName(),
                    request.getSlug(),
                    request.getEmail(),
                    request.getPhoneNumber(),
                    request.getTenantType()
            );
        }

        tenant.assignTenant(tenantId);

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    // ------------------------------------------------------------
    // SUSPEND TENANT
    // ------------------------------------------------------------
    @Override
    public TenantResponse suspendTenant(UUID tenantId, UUID targetTenantId, SuspendTenantRequest request) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        tenant.suspend();

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    // ------------------------------------------------------------
    // ACTIVATE TENANT
    // ------------------------------------------------------------
    @Override
    public TenantResponse activateTenant(UUID tenantId, UUID targetTenantId) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        tenant.activate();

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    // ------------------------------------------------------------
    // UPDATE STATUS
    // ------------------------------------------------------------
    @Override
    public TenantResponse updateTenantStatus(UUID tenantId, UUID targetTenantId, UpdateTenantStatusRequest request) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        tenant.updateStatus(request.getStatus());

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    // ------------------------------------------------------------
    // UPDATE SUBSCRIPTION
    // ------------------------------------------------------------
    @Override
    public TenantResponse updateTenantSubscription(UUID tenantId, UUID targetTenantId, UpdateTenantSubscriptionRequest request) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        tenant.updateSubscription(request.getSubscriptionStatus());

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    // ------------------------------------------------------------
    // UPDATE BRANDING
    // ------------------------------------------------------------
    @Override
    public TenantResponse updateTenantBranding(UUID tenantId, UUID targetTenantId, UpdateTenantBrandingRequest request) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        tenant.updateBranding(request.getBrandingSettings());

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    // ------------------------------------------------------------
    // ASSIGN OWNER
    // ------------------------------------------------------------
    @Override
    public TenantResponse assignTenantOwner(UUID tenantId, UUID targetTenantId, AssignTenantOwnerRequest request) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        tenant.assignOwner(
                request.getName(),
                request.getEmail(),
                request.getPhoneNumber(),
                request.getIdentifier()
        );

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    // ------------------------------------------------------------
    // CHANGE OWNER
    // ------------------------------------------------------------
    @Override
    public TenantResponse changeTenantOwner(UUID tenantId, UUID targetTenantId, ChangeTenantOwnerRequest request) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        tenant.changeOwner(
                request.getName(),
                request.getEmail(),
                request.getPhoneNumber(),
                request.getIdentifier()
        );

        return tenantMapper.toResponse(tenantRepository.save(tenant));
    }

    // ------------------------------------------------------------
    // GET TENANT
    // ------------------------------------------------------------
    @Override
    public TenantResponse getTenant(UUID tenantId, UUID targetTenantId) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        return tenantMapper.toResponse(tenant);
    }

    // ------------------------------------------------------------
    // HELPERS
    // ------------------------------------------------------------
    private Tenant findTenant(UUID targetTenantId) {
        return tenantRepository.findById(targetTenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));
    }

    private void validateTenantAccess(UUID actorTenantId, Tenant tenant) {
        if (actorTenantId == null) {
            throw new IllegalArgumentException("Actor tenant cannot be null");
        }

        if (tenant.getOrganizationId() == null || !tenant.getOrganizationId().equals(actorTenantId)) {
            throw new SecurityException("Cross-tenant access denied");
        }
    }
}