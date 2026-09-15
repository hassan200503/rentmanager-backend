package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import com.rentmanager.modules.tenant.application.command.handler.CreateTenantCommandHandler;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Handles first-time landlord onboarding:
 * <ol>
 *   <li>POST /tenant — creates the Tenant for a ROLE_PENDING_ONBOARDING user.</li>
 *   <li>GET /progress — returns setup wizard progress for an existing landlord.</li>
 *   <li>POST /complete — marks onboarding done and activates the tenant.</li>
 * </ol>
 *
 * <p>clerkOrgId is deliberately read from the verified JWT rather than the
 * request body — a client must never be able to claim an arbitrary Clerk org.
 *
 * <p>After POST /tenant succeeds the user's JWT still carries
 * ROLE_PENDING_ONBOARDING; the frontend must force a token refresh before
 * redirecting to /dashboard (see use-onboard-tenant-mutation.ts).
 */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private static final String CLAIM_CLERK_ORG_ID = "tenant_id";

    private final CreateTenantCommandHandler createTenantCommandHandler;
    private final TenantRepository tenantRepository;
    private final PropertyRepository propertyRepository;

    public OnboardingController(
            CreateTenantCommandHandler createTenantCommandHandler,
            TenantRepository tenantRepository,
            PropertyRepository propertyRepository
    ) {
        this.createTenantCommandHandler = createTenantCommandHandler;
        this.tenantRepository = tenantRepository;
        this.propertyRepository = propertyRepository;
    }

    // ── Step 0: Create organisation ──────────────────────────────────────────

    @PostMapping("/tenant")
    @PreAuthorize("hasAuthority('ROLE_PENDING_ONBOARDING')")
    public ResponseEntity<OnboardingTenantResponse> onboardTenant(
            @RequestBody OnboardingTenantRequest request,
            Authentication authentication
    ) {
        String clerkOrgId = extractClerkOrgId(authentication);

        Tenant tenant = createTenantCommandHandler.handle(
                clerkOrgId,
                request.name(),
                request.email(),
                request.phoneNumber(),
                request.address(),
                request.tenantType() != null ? request.tenantType() : TenantType.TRIAL
        );

        return ResponseEntity.ok(new OnboardingTenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus().name()
        ));
    }

    // ── Setup wizard progress ─────────────────────────────────────────────────

    /**
     * Returns the current onboarding / setup wizard state for the authenticated
     * landlord. Used by the dashboard SetupChecklist component to show which
     * steps remain without requiring a page reload between steps.
     *
     * <p>Accessible to OWNER and MANAGER so both can see progress, but only OWNER
     * can call POST /complete (only an org owner should finalise setup).
     */
    @GetMapping("/progress")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<OnboardingProgressResponse>> getProgress(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID tenantId = requireTenantId(user);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        boolean hasProperties = propertyRepository
                .findAllByTenantId(tenantId, PageRequest.of(0, 1))
                .hasContent();

        boolean paymentConfigured = tenant.getDarajaCredentials() != null
                && tenant.getDarajaCredentials().isConfigured();

        OnboardingProgressResponse progress = new OnboardingProgressResponse(
                tenant.isOnboardingCompleted(),
                tenant.getStatus().name(),
                hasProperties,
                paymentConfigured
        );

        return ResponseEntity.ok(ApiResponse.ok("Onboarding progress retrieved", progress));
    }

    // ── Mark setup complete ───────────────────────────────────────────────────

    /**
     * Marks onboarding as completed and activates the tenant.
     *
     * <p>Only the org OWNER can complete onboarding — this is a one-time
     * lifecycle operation that sets tenant.active = true and allows the account
     * to appear in admin reporting.
     *
     * <p>Idempotent: calling again after onboarding is already complete is safe
     * (activate() and completeOnboarding() both guard their transitions).
     */
    @PostMapping("/complete")
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<OnboardingProgressResponse>> complete(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID tenantId = requireTenantId(user);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found"));

        tenant.completeOnboarding();
        tenant.activate();
        tenantRepository.save(tenant);

        boolean hasProperties = propertyRepository
                .findAllByTenantId(tenantId, PageRequest.of(0, 1))
                .hasContent();

        OnboardingProgressResponse progress = new OnboardingProgressResponse(
                tenant.isOnboardingCompleted(),
                tenant.getStatus().name(),
                hasProperties,
                tenant.getDarajaCredentials() != null && tenant.getDarajaCredentials().isConfigured()
        );

        return ResponseEntity.ok(ApiResponse.ok("Setup completed", progress));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractClerkOrgId(Authentication authentication) {
        Object credentials = authentication.getCredentials();

        if (!(credentials instanceof Jwt jwt)) {
            throw new IllegalStateException(
                    "Expected a verified Jwt as credentials — got " +
                            (credentials == null ? "null" : credentials.getClass().getName()));
        }

        String clerkOrgId = jwt.getClaimAsString(CLAIM_CLERK_ORG_ID);

        if (clerkOrgId == null || clerkOrgId.isBlank()) {
            throw new IllegalStateException(
                    "No Clerk organization associated with this account. " +
                            "Create/select an organization in Clerk before onboarding.");
        }

        return clerkOrgId;
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record OnboardingTenantRequest(
            String name,
            String email,
            String phoneNumber,
            String address,
            TenantType tenantType
    ) {}

    public record OnboardingTenantResponse(
            UUID tenantId,
            String name,
            String slug,
            String status
    ) {}

    /**
     * Represents the landlord's current setup progress.
     *
     * @param onboardingCompleted true once POST /complete has been called
     * @param tenantStatus        PENDING until onboarding complete, then ACTIVE
     * @param hasProperties       at least one property exists in the portfolio
     * @param paymentConfigured   Daraja credentials have been saved and configured
     */
    public record OnboardingProgressResponse(
            boolean onboardingCompleted,
            String tenantStatus,
            boolean hasProperties,
            boolean paymentConfigured
    ) {}
}
