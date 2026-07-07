package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.modules.tenant.application.command.handler.CreateTenantCommandHandler;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles first-time landlord onboarding: a Clerk-authenticated user with
 * no local Tenant yet (ROLE_PENDING_ONBOARDING — see
 * ClerkJwtAuthenticationConverter) submits their business details here to
 * create their Tenant.
 *
 * clerkOrgId is deliberately read from the verified JWT (via
 * Authentication.getCredentials(), which ClerkAuthenticationToken exposes
 * as the raw Jwt) rather than accepted from the request body — a client
 * must never be able to claim an arbitrary Clerk org as their own.
 *
 * After this call succeeds, the user's role/tenant link is NOT updated
 * immediately — that happens automatically the next time their JWT is
 * converted (ClerkJwtAuthenticationConverter.resolveTenantId(), which
 * looks up the tenant by clerkOrgId and assigns first-user-becomes-OWNER).
 * The frontend should force a token refresh / re-fetch of /users/me after
 * a successful onboarding response.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
public class OnboardingController {

    private static final String CLAIM_CLERK_ORG_ID = "tenant_id";

    private final CreateTenantCommandHandler createTenantCommandHandler;

    public OnboardingController(CreateTenantCommandHandler createTenantCommandHandler) {
        this.createTenantCommandHandler = createTenantCommandHandler;
    }

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
                request.tenantType()
        );

        return ResponseEntity.ok(new OnboardingTenantResponse(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getStatus().name()
        ));
    }

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

    public record OnboardingTenantRequest(
            String name,
            String email,
            String phoneNumber,
            String address,
            TenantType tenantType
    ) {}

    public record OnboardingTenantResponse(
            java.util.UUID tenantId,
            String name,
            String slug,
            String status
    ) {}
}