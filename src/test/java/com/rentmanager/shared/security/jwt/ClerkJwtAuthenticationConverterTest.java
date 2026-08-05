package com.rentmanager.shared.security.jwt;

import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers the acceptance criteria listed in the RBAC handoff doc, §6.2:
 *  - First-user-of-tenant -> OWNER assigned correctly
 *  - Second (non-first, non-invited) user linking to an existing tenant -> STAFF
 *  - Invited user (role already set via createInvited) never overwritten, even on first login
 *  - Both ROLE_LANDLORD and the fine-grained authority are granted together
 *  - Regression: ROLE_TENANT and ROLE_PENDING_ONBOARDING paths unaffected by this build
 *
 * NOTE: Tenant is mocked rather than constructed, since Tenant.java itself
 * was not shown in this conversation. Only the getId():UUID call visible in
 * ClerkJwtAuthenticationConverter.resolveTenantId() is relied on here -- no
 * other assumption is made about Tenant's shape. If Tenant.java is later
 * provided and its real construction differs, this should be revisited.
 */
@ExtendWith(MockitoExtension.class)
class ClerkJwtAuthenticationConverterTest {

    private static final String CLERK_USER_ID = "clerk_user_123";
    private static final String CLERK_ORG_ID = "clerk_org_456";
    private static final String EMAIL = "person@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantProfileRepository tenantProfileRepository;

    @Mock
    private Tenant tenant;

    private ClerkJwtAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ClerkJwtAuthenticationConverter(userRepository, tenantRepository, tenantProfileRepository);
    }

    @AfterEach
    void tearDown() {
        // TenantContext is ThreadLocal-based and has no request-scoped clear
        // call site anywhere in production code (flagged in the handoff doc,
        // §5) -- tests must clean up explicitly to avoid bleeding state
        // across test methods on the same thread.
        TenantContext.clear();
    }

    private Jwt jwtWithOrg(String orgId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(CLERK_USER_ID);
        when(jwt.getClaimAsString("tenant_id")).thenReturn(orgId);
        when(jwt.getClaimAsString("email")).thenReturn(EMAIL);
        return jwt;
    }

    private Jwt jwtWithPlatformRole(String platformRole) {
        Jwt jwt = jwtWithOrg(null);
        when(jwt.getClaimAsString("platformRole")).thenReturn(platformRole);
        return jwt;
    }

    private Set<String> authorityStrings(AbstractAuthenticationToken token) {
        return token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    void firstUserOfTenant_isAssignedOwner() {
        UUID tenantId = UUID.randomUUID();
        User newUser = User.createFromClerk(CLERK_USER_ID, EMAIL);

        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByClerkOrgId(CLERK_ORG_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.existsByTenantId(tenantId)).thenReturn(false); // no existing users -> first

        AbstractAuthenticationToken token = converter.convert(jwtWithOrg(CLERK_ORG_ID));

        AuthenticatedUser principal = (AuthenticatedUser) token.getPrincipal();
        assertThat(principal.getTenantId()).isEqualTo(tenantId);
        assertThat(authorityStrings(token)).containsExactlyInAnyOrder(
                "ROLE_LANDLORD", "ROLE_LANDLORD_OWNER"
        );

        // save() is legitimately called twice for a genuinely new signup:
        // once by resolveOrProvisionUser() to JIT-create the local User row,
        // and again by resolveTenantId() after tenant+role assignment. User
        // is mutable and both calls receive the same object reference, so
        // by verification time both captured invocations reflect the final
        // (OWNER, tenantId-assigned) state — hence times(2), not times(1).
        verify(userRepository, times(2)).save(argThat(u -> u.hasRole(UserRole.OWNER) && tenantId.equals(u.getTenantId())));
    }

    @Test
    void secondNonInvitedUser_linkingExistingTenant_defaultsToStaff() {
        UUID tenantId = UUID.randomUUID();

        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByClerkOrgId(CLERK_ORG_ID)).thenReturn(Optional.of(tenant));
        when(userRepository.existsByTenantId(tenantId)).thenReturn(true); // tenant already has users

        AbstractAuthenticationToken token = converter.convert(jwtWithOrg(CLERK_ORG_ID));

        assertThat(authorityStrings(token)).containsExactlyInAnyOrder(
                "ROLE_LANDLORD", "ROLE_LANDLORD_STAFF"
        );
        // Same double-save shape as the first-user/OWNER test above (see
        // comment there) — JIT-provisioning save + tenant/role-assignment save.
        verify(userRepository, times(2)).save(argThat(u -> u.hasRole(UserRole.STAFF)));
    }

    @Test
    void invitedUser_roleIsNeverOverwritten_evenOnFirstLogin() {
        UUID tenantId = UUID.randomUUID();
        User invitedUser = User.createInvited(
                CLERK_USER_ID, EMAIL, "Jane", "Doe", tenantId, UserRole.MANAGER
        );

        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.of(invitedUser));
        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByClerkOrgId(CLERK_ORG_ID)).thenReturn(Optional.of(tenant));

        AbstractAuthenticationToken token = converter.convert(jwtWithOrg(CLERK_ORG_ID));

        assertThat(authorityStrings(token)).containsExactlyInAnyOrder(
                "ROLE_LANDLORD", "ROLE_LANDLORD_MANAGER"
        );
        assertThat(invitedUser.getRole()).isEqualTo(UserRole.MANAGER);

        // tenantId on the invited user already equals the resolved tenantId,
        // so tenantLinkChanged is false and the whole assignTenant/assignRole/
        // save block in resolveTenantId() must never run.
        verify(userRepository, never()).existsByTenantId(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void bothRoleLandlordAndFineGrainedAuthority_areGrantedTogether() {
        UUID tenantId = UUID.randomUUID();
        User invitedUser = User.createInvited(
                CLERK_USER_ID, EMAIL, "Sam", "Owner", tenantId, UserRole.OWNER
        );

        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.of(invitedUser));
        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByClerkOrgId(CLERK_ORG_ID)).thenReturn(Optional.of(tenant));

        AbstractAuthenticationToken token = converter.convert(jwtWithOrg(CLERK_ORG_ID));

        Set<String> authorities = authorityStrings(token);
        assertThat(authorities).contains("ROLE_LANDLORD");
        assertThat(authorities).contains("ROLE_LANDLORD_OWNER");
        assertThat(authorities).hasSize(2);
    }

    @Test
    void regression_rolesTenant_pathUnaffectedByRbacBuild() {
        // No clerk org claim at all -> resolveTenantId short-circuits to null,
        // falling through to the pre-existing renter-detection branch.
        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantProfileRepository.existsByClerkUserId(CLERK_USER_ID)).thenReturn(true);

        AbstractAuthenticationToken token = converter.convert(jwtWithOrg(null));

        assertThat(authorityStrings(token)).containsExactly("ROLE_TENANT");
        verifyNoInteractions(tenantRepository);
    }

    @Test
    void regression_rolePendingOnboarding_pathUnaffectedByRbacBuild() {
        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantProfileRepository.existsByClerkUserId(CLERK_USER_ID)).thenReturn(false);

        AbstractAuthenticationToken token = converter.convert(jwtWithOrg(null));

        assertThat(authorityStrings(token)).containsExactly("ROLE_PENDING_ONBOARDING");
        verifyNoInteractions(tenantRepository);
    }

    @Test
    void clerkOrgPresent_butNoLocalTenantYet_resolvesNullTenantId_notAutoProvisioned() {
        // Deliberate business decision per the handoff doc: tenants are NOT
        // auto-provisioned. A Clerk org claim with no matching local Tenant
        // row must authenticate with a null tenantId rather than fabricate one.
        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantRepository.findByClerkOrgId(CLERK_ORG_ID)).thenReturn(Optional.empty());
        when(tenantProfileRepository.existsByClerkUserId(CLERK_USER_ID)).thenReturn(false);

        AbstractAuthenticationToken token = converter.convert(jwtWithOrg(CLERK_ORG_ID));

        AuthenticatedUser principal = (AuthenticatedUser) token.getPrincipal();
        assertThat(principal.getTenantId()).isNull();
        assertThat(authorityStrings(token)).containsExactly("ROLE_PENDING_ONBOARDING");
        verify(userRepository, never()).existsByTenantId(any());
    }

    // ------------------------------------------------------------------
    // PLATFORM OWNER / ADMIN (platformRole claim, Phase 1 admin surface)
    // ------------------------------------------------------------------

    @Test
    void platformOwnerClaim_grantsOwnerAndAdminAuthorities() {
        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantProfileRepository.existsByClerkUserId(CLERK_USER_ID)).thenReturn(false);

        AbstractAuthenticationToken token = converter.convert(jwtWithPlatformRole("OWNER"));

        assertThat(authorityStrings(token)).containsExactlyInAnyOrder(
                "ROLE_PLATFORM_OWNER", "ROLE_PLATFORM_ADMIN", "ROLE_PENDING_ONBOARDING"
        );
    }

    @Test
    void platformAdminClaim_grantsAdminAuthorityOnly() {
        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantProfileRepository.existsByClerkUserId(CLERK_USER_ID)).thenReturn(false);

        AbstractAuthenticationToken token = converter.convert(jwtWithPlatformRole("ADMIN"));

        assertThat(authorityStrings(token)).containsExactlyInAnyOrder(
                "ROLE_PLATFORM_ADMIN", "ROLE_PENDING_ONBOARDING"
        );
    }

    @Test
    void platformOwnerClaim_withTenantMembership_keepsLandlordAuthorities() {
        UUID tenantId = UUID.randomUUID();
        User invitedUser = User.createInvited(
                CLERK_USER_ID, EMAIL, "Pat", "Owner", tenantId, UserRole.OWNER
        );

        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.of(invitedUser));
        when(tenant.getId()).thenReturn(tenantId);
        when(tenantRepository.findByClerkOrgId(CLERK_ORG_ID)).thenReturn(Optional.of(tenant));

        Jwt jwt = jwtWithOrg(CLERK_ORG_ID);
        when(jwt.getClaimAsString("platformRole")).thenReturn("OWNER");

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(authorityStrings(token)).containsExactlyInAnyOrder(
                "ROLE_LANDLORD", "ROLE_LANDLORD_OWNER",
                "ROLE_PLATFORM_OWNER", "ROLE_PLATFORM_ADMIN"
        );
    }

    @Test
    void unknownPlatformRole_isIgnored() {
        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantProfileRepository.existsByClerkUserId(CLERK_USER_ID)).thenReturn(false);

        AbstractAuthenticationToken token = converter.convert(jwtWithPlatformRole("CUSTOMER_SERVICE"));

        assertThat(authorityStrings(token)).containsExactly("ROLE_PENDING_ONBOARDING");
    }

    @Test
    void missingPlatformRole_isIgnored() {
        when(userRepository.findByClerkUserId(CLERK_USER_ID)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenantProfileRepository.existsByClerkUserId(CLERK_USER_ID)).thenReturn(false);

        AbstractAuthenticationToken token = converter.convert(jwtWithOrg(null));

        assertThat(authorityStrings(token)).containsExactly("ROLE_PENDING_ONBOARDING");
    }
}