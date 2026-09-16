package com.rentmanager.modules.tenant.application.command.handler;

import com.rentmanager.modules.platformsettings.domain.model.PlatformSettings;
import com.rentmanager.modules.platformsettings.domain.repository.PlatformSettingsRepository;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

/**
 * Handles Tenant creation workflow.
 *
 * Fixes applied vs. previous version:
 *  - Tenant.create(...) args were positionally shifted (name/email/phone/
 *    address/referenceCode were passed into tenantCode/name/slug/email/
 *    phoneNumber slots respectively). Now mapped correctly.
 *  - slug was never generated; Tenant.create() requires one (unique,
 *    non-blank). Derived from name + a short random suffix for uniqueness.
 *  - tenantCode is now server-generated (KE-XXXXXXXX) rather than accepted
 *    as client input (previously "referenceCode"). It's a unique DB
 *    column with no business meaning to the landlord, so letting clients
 *    supply it just invited avoidable unique-constraint failures on the
 *    onboarding form.
 *  - organizationId was never assigned, so validateTenantAccess() in
 *    TenantCommandServiceImpl would reject every request for tenants
 *    created via this handler. It's self-referential (organizationId ==
 *    tenant's own id), matching the pattern used elsewhere in the tenant
 *    module (see TenantCommandServiceImpl.createTenant()).
 *  - clerkOrgId was never assigned, so ClerkJwtAuthenticationConverter's
 *    tenantRepository.findByClerkOrgId(clerkOrgId) lookup would never
 *    resolve this tenant on subsequent logins.
 *  - Defaults set for the Kenyan market: KES currency, Africa/Nairobi
 *    timezone, en-KE locale, so downstream billing/reporting/M-Pesa flows
 *    don't hit null locale data.
 *  - tenant.assignTenant(id) call updated to tenant.assignOrganization(id)
 *    (broader event-publish sweep, item 4.3): Tenant's own assignTenant(UUID)
 *    method was removed — it collided in name/signature with
 *    BaseTenantEntity.assignTenant(UUID), the once-only isolation-enforcing
 *    method other aggregates in this codebase rely on. assignOrganization(UUID)
 *    does the same organizationId assignment (with an added null check);
 *    behavior here is unchanged since id is always a freshly-generated,
 *    non-null UUID at this call site.
 *  - Trial duration is now read from PlatformSettings so the owner can
 *    configure it via the admin console (PUT /admin/settings). Falls back to
 *    the domain constant (30 days) if no settings row exists yet.
 */
@Component
public class CreateTenantCommandHandler {

    private static final String DEFAULT_TIMEZONE = "Africa/Nairobi";
    private static final String DEFAULT_CURRENCY = "KES";
    private static final String DEFAULT_LOCALE = "en-KE";

    private final TenantRepository tenantRepository;
    private final PlatformSettingsRepository platformSettingsRepository;

    public CreateTenantCommandHandler(
            TenantRepository tenantRepository,
            PlatformSettingsRepository platformSettingsRepository
    ) {
        this.tenantRepository = tenantRepository;
        this.platformSettingsRepository = platformSettingsRepository;
    }

    /**
     * @param clerkOrgId the Clerk organization ID this tenant maps to.
     *                   Must come from a verified JWT claim server-side —
     *                   never trust this from client-supplied request body.
     */
    public Tenant handle(
            String clerkOrgId,
            String name,
            String email,
            String phoneNumber,
            String address,
            TenantType tenantType
    ) {

        if (clerkOrgId == null || clerkOrgId.isBlank()) {
            throw new IllegalArgumentException("Clerk organization ID must not be empty");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tenant name must not be empty");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Tenant email must not be empty");
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Tenant phone number must not be empty");
        }

        if (tenantType == null) {
            throw new IllegalArgumentException("Tenant type must not be null");
        }

        // Idempotency guard: a double-submit of the onboarding form (double
        // click, retry after timeout, etc.) must not create two tenants for
        // the same Clerk org.
        tenantRepository.findByClerkOrgId(clerkOrgId).ifPresent(existing -> {
            throw new IllegalStateException(
                    "A tenant already exists for this organization (id=" + existing.getId() + ")");
        });

        String slug = generateSlug(name);
        String tenantCode = generateTenantCode();

        Tenant tenant = Tenant.create(
                tenantCode,      // tenantCode — server-generated, not client input
                name,            // name
                slug,            // slug
                email,           // email
                phoneNumber,     // phoneNumber
                tenantType       // type
        );

        // Assign ID ourselves (BaseEntity allows this — @PrePersist only
        // generates one if null) so organizationId can be set correctly
        // in a single save, rather than needing a save-then-update.
        UUID id = UUID.randomUUID();
        tenant.setId(id);

        tenant.assignOrganization(id);         // organizationId = self
        tenant.assignClerkOrgId(clerkOrgId);
        tenant.updateLocalization(DEFAULT_TIMEZONE, DEFAULT_CURRENCY, DEFAULT_LOCALE);

        if (address != null && !address.isBlank()) {
            tenant.updateAddress(address);
        }

        // Override the constructor default with the owner-configured trial
        // duration. If the platform_settings row doesn't exist yet (fresh
        // deploy, no owner has logged in), fall back to the domain default.
        int trialDays = platformSettingsRepository.findSingleton()
                .map(PlatformSettings::getTrialDurationDays)
                .orElse(PlatformSettings.DEFAULT_TRIAL_DURATION_DAYS);
        tenant.startTrial(trialDays);

        return tenantRepository.save(tenant);
    }

    private String generateTenantCode() {
        // Kenya-market prefix + short random suffix. Uniqueness is enforced
        // by the DB constraint (tenant_code is unique); collision odds with
        // 8 hex chars are negligible at this scale, but if this handler
        // ever needs to be bulletproof against retries hitting the unique
        // constraint, wrap the save() call with a retry-with-new-code loop.
        return "KE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String generateSlug(String name) {
        String base = name.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");

        if (base.isBlank()) {
            base = "tenant";
        }

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return base + "-" + suffix;
    }
}
