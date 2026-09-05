package com.rentmanager.modules.tenant.application.command.service;

import com.rentmanager.modules.tenant.application.dto.request.*;
import com.rentmanager.modules.tenant.application.dto.response.DarajaCredentialsStatusResponse;
import com.rentmanager.modules.tenant.application.dto.response.DarajaCredentialsTestResponse;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import com.rentmanager.modules.tenant.application.mapper.TenantMapper;

import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import org.springframework.stereotype.Service;

import java.util.UUID;
import com.rentmanager.modules.audit.application.service.FinancialAuditService;

@Service
public class TenantCommandServiceImpl implements TenantCommandService {

    private final TenantRepository tenantRepository;
    private final com.rentmanager.modules.integration.bridge.LandlordDarajaVerifier darajaVerifier;
    private final FinancialAuditService financialAuditService;
    private final TenantMapper tenantMapper;

    public TenantCommandServiceImpl(
            TenantRepository tenantRepository,
            TenantMapper tenantMapper,
            FinancialAuditService financialAuditService,
            com.rentmanager.modules.integration.bridge.LandlordDarajaVerifier darajaVerifier
    ) {
        this.tenantRepository = tenantRepository;
        this.tenantMapper = tenantMapper;
        this.financialAuditService = financialAuditService;
        this.darajaVerifier = darajaVerifier;
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

        // Was tenant.assignTenant(tenantId) — renamed call, same behavior.
        // See Tenant.java's "SERVICE COMPATIBILITY METHODS" note: the old
        // assignTenant(UUID) collided in name/signature with
        // BaseTenantEntity.assignTenant(UUID) and has been removed;
        // assignOrganization(UUID) does the same assignment with an added
        // null check.
        tenant.assignOrganization(tenantId);

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
    // GET DARAJA CREDENTIALS STATUS
    // ------------------------------------------------------------
    @Override
    public DarajaCredentialsStatusResponse getDarajaCredentialsStatus(UUID tenantId, UUID targetTenantId) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        boolean configured = tenant.getDarajaCredentials().isConfigured();

        return new DarajaCredentialsStatusResponse(
                configured, tenant.getCollectionMode().name());
    }

    /**
     * Tests the landlord's saved Daraja credentials against Safaricom.
     *
     * <p>Before this existed, wrong credentials saved happily and failed
     * later — at the moment a prospective renter tried to pay a deposit,
     * with an error only the server log saw. The landlord's first signal
     * that their M-Pesa setup was broken was a renter who could not pay.
     *
     * <p>Deliberately tests what is SAVED rather than what is being typed:
     * a green tick against unsaved input would not tell the landlord whether
     * the credentials the payment path actually loads are the working ones.
     */
    // Deliberately not @Transactional: the only database work is a single
    // read, and the Safaricom call that follows must not run inside a
    // transaction — holding a connection open across a third party's
    // latency is how a slow provider becomes a pool exhaustion.
    @Override
    public DarajaCredentialsTestResponse testDarajaCredentials(UUID tenantId, UUID targetTenantId) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        var credentials = tenant.getDarajaCredentials();
        if (credentials == null || !credentials.isConfigured()) {
            return new DarajaCredentialsTestResponse(
                    false,
                    "No credentials saved",
                    "Save your Consumer Key, Consumer Secret, Shortcode and Passkey before testing.",
                    darajaVerifier.environmentBaseUrl(),
                    DarajaCredentialsTestResponse.SCOPE_NOTE);
        }

        var result = darajaVerifier.verify(
                credentials.getConsumerKey(), credentials.getConsumerSecret());

        return new DarajaCredentialsTestResponse(
                result.ok(),
                result.message(),
                result.error(),
                darajaVerifier.environmentBaseUrl(),
                DarajaCredentialsTestResponse.SCOPE_NOTE);
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

        // Compares against tenant.getId() rather than tenant.getOrganizationId():
        // organizationId is only ever set self-referentially (organizationId ==
        // id) by CreateTenantCommandHandler, with no DB constraint enforcing
        // that invariant. getId() is the tenant's actual primary key and is
        // always correct, so this check no longer depends on a convention
        // nothing guarantees.
        if (!tenant.getId().equals(actorTenantId)) {
            throw new SecurityException("Cross-tenant access denied");
        }
    }

    // ------------------------------------------------------------
    // CONFIGURE DARAJA CREDENTIALS
    // ------------------------------------------------------------
    @Override
    public DarajaCredentialsStatusResponse configureDarajaCredentials(
            UUID tenantId,
            UUID targetTenantId,
            ConfigureDarajaCredentialsRequest request
    ) {

        Tenant tenant = findTenant(targetTenantId);
        validateTenantAccess(tenantId, tenant);

        tenant.configureDarajaCredentials(
                request.getConsumerKey(),
                request.getConsumerSecret(),
                request.getBusinessShortCode(),
                request.getPasskey()
        );

        tenantRepository.save(tenant);

        // These credentials decide which paybill a landlord's rent lands in.
        // Rotating them redirects money as surely as changing the payout
        // number does, so the rotation is recorded — no credential material,
        // only that it happened, for whom, and by whom. Recording more at a
        // rotation would create the leak the row exists to detect.
        //
        // The platform-level integrations path has its own audit trail
        // (integration_audit_log, V68); this per-landlord path had none.
        financialAuditService.paymentCredentialsChanged(
                targetTenantId, "DARAJA", "LANDLORD");

        return new DarajaCredentialsStatusResponse(
                true, tenant.getCollectionMode().name());
    }

}