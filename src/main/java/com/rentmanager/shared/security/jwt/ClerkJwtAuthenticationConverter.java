package com.rentmanager.shared.security.jwt;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class ClerkJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_TENANT_ID = "tenant_id";
    private static final String CLAIM_EMAIL = "email";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;

    public ClerkJwtAuthenticationConverter(
            UserRepository userRepository,
            TenantRepository tenantRepository
    ) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    public AbstractAuthenticationToken convert(Jwt jwt) {

        String clerkUserId = jwt.getSubject();
        String clerkOrgId = jwt.getClaimAsString(CLAIM_TENANT_ID);
        String email = jwt.getClaimAsString(CLAIM_EMAIL);

        User user = resolveOrProvisionUser(clerkUserId, email);
        UUID resolvedTenantId = resolveTenantId(clerkOrgId, user);

        Set<SimpleGrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("ROLE_USER")
        );

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                resolvedTenantId,
                user.getEmail(),
                "",
                user.isActive(),
                authorities
        );

        return new ClerkAuthenticationToken(authenticatedUser, jwt, authorities);
    }

    /**
     * Just-in-time provisioning for users: a verified Clerk identity always
     * gets a corresponding local User row, created on first sight.
     */
    private User resolveOrProvisionUser(String clerkUserId, String email) {

        Optional<User> existing = userRepository.findByClerkUserId(clerkUserId);

        if (existing.isPresent()) {
            return existing.get();
        }

        User newUser = User.createFromClerk(clerkUserId, email != null ? email : "unknown@clerk.user");
        return userRepository.save(newUser);
    }

    /**
     * Tenants are NOT auto-provisioned (deliberate business decision).
     * If no local Tenant exists for this Clerk org yet, we authenticate
     * the user with a null tenantId, so middleware/route guards can route
     * them to an explicit onboarding flow instead.
     */
    private UUID resolveTenantId(String clerkOrgId, User user) {

        if (clerkOrgId == null || clerkOrgId.isBlank()) {
            return null;
        }

        Optional<Tenant> tenant = tenantRepository.findByClerkOrgId(clerkOrgId);

        if (tenant.isEmpty()) {
            return null;
        }

        UUID tenantId = tenant.get().getId();

        // Keep the user's tenant link in sync once a tenant exists.
        if (user.getTenantId() == null || !user.getTenantId().equals(tenantId)) {
            user.assignTenant(tenantId);
            userRepository.save(user);
        }

        return tenantId;
    }
}