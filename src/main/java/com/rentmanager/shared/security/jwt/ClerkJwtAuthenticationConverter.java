package com.rentmanager.shared.security.jwt;

import com.rentmanager.modules.identity.clerk.ClerkService;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ClerkJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String CLAIM_TENANT_ID = "tenant_id";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_PLATFORM_ROLE = "platformRole";

    private static final String ROLE_PLATFORM_OWNER = "ROLE_PLATFORM_OWNER";
    private static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";
    private static final String PLATFORM_ROLE_OWNER = "OWNER";
    private static final String PLATFORM_ROLE_ADMIN = "ADMIN";

    private static final String ROLE_LANDLORD = "ROLE_LANDLORD";
    private static final String ROLE_LANDLORD_OWNER = "ROLE_LANDLORD_OWNER";
    private static final String ROLE_LANDLORD_MANAGER = "ROLE_LANDLORD_MANAGER";
    private static final String ROLE_LANDLORD_STAFF = "ROLE_LANDLORD_STAFF";
    private static final String ROLE_TENANT = "ROLE_TENANT";
    // Authenticated via a valid Clerk token, but not yet linked to a landlord
    // account (Clerk Org) or a TenantProfile under any landlord. Route guards
    // should send these users to an onboarding flow rather than treat them
    // as fully authorized for either role.
    private static final String ROLE_PENDING_ONBOARDING = "ROLE_PENDING_ONBOARDING";

    /**
     * Stamped when a Clerk token carries no email claim. Deliberately named
     * so a grep for it finds every place that must not treat it as a real
     * address — it is non-blank, so it passes naive null/blank guards.
     */
    public static final String PLACEHOLDER_EMAIL = "unknown@clerk.user";

    // How long a resolved identity is trusted before re-querying. Long
    // enough to absorb a dashboard's typical burst of parallel requests
    // (all land within milliseconds of each other on page load); short
    // enough that a role or tenant-binding change made through a different
    // code path (e.g. an admin promoting a STAFF user to MANAGER) is picked
    // up within a minute, with no explicit cache-invalidation hook needed
    // at every place that could mutate a User's role/tenant.
    private static final long CACHE_TTL_MILLIS = 60_000;
    // Above this size, a cache write also sweeps expired entries — bounds
    // memory for a long-running JVM without needing a dedicated eviction
    // thread or a caching library (see the class-level rationale for
    // avoiding one: this project deliberately keeps caching in-process and
    // dependency-free at this scale).
    private static final int CACHE_SWEEP_THRESHOLD = 5_000;

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final ObjectProvider<ClerkService> clerkServiceProvider;
    private final Clock clock;
    private final Map<String, CachedIdentity> identityCache = new ConcurrentHashMap<>();

    /**
     * Clock is an ObjectProvider, same resilience pattern as
     * clerkServiceProvider above: @WebMvcTest slices across this codebase
     * construct this converter as part of the security filter chain but
     * don't load ClockConfig (a plain @Configuration bean, out of scope for
     * a web-layer slice), so a required Clock dependency would fail
     * ApplicationContext startup for every one of those tests. Falls back
     * to the real system clock when no bean is available — identical
     * behavior to what this class had before the cache was introduced.
     */
    public ClerkJwtAuthenticationConverter(
            UserRepository userRepository,
            TenantRepository tenantRepository,
            TenantProfileRepository tenantProfileRepository,
            ObjectProvider<ClerkService> clerkServiceProvider,
            ObjectProvider<Clock> clockProvider
    ) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.tenantProfileRepository = tenantProfileRepository;
        this.clerkServiceProvider = clerkServiceProvider;
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Override
    @Transactional
    public AbstractAuthenticationToken convert(Jwt jwt) {

        String clerkUserId = jwt.getSubject();
        String clerkOrgId = jwt.getClaimAsString(CLAIM_TENANT_ID);
        String emailClaim = jwt.getClaimAsString(CLAIM_EMAIL);
        String platformRole = jwt.getClaimAsString(CLAIM_PLATFORM_ROLE);

        CachedIdentity identity = resolveIdentity(clerkUserId, clerkOrgId, emailClaim, platformRole);

        Set<SimpleGrantedAuthority> authorities =
                resolveAuthorities(identity.tenantId(), identity.role(), identity.isRenterProfile());
        authorities = withPlatformAuthorities(authorities, platformRole);

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                identity.userId(),
                identity.tenantId(),
                identity.email(),
                "",
                identity.active(),
                authorities
        );

        TenantContext.setTenantId(identity.tenantId());
        TenantContext.setUserId(identity.userId());
        return new ClerkAuthenticationToken(authenticatedUser, jwt, authorities);
    }

    /**
     * Resolves {clerkUserId, clerkOrgId} -> the local identity (user id,
     * tenant id, role, active flag, renter-profile flag), caching the
     * result for CACHE_TTL_MILLIS. Before this cache existed, EVERY
     * authenticated request ran findByClerkUserId + findByClerkOrgId (plus
     * a third query, existsByClerkUserId, on the renter/pending-onboarding
     * path) — a dashboard firing 5 parallel calls meant 10-15 extra round
     * trips per page load. See the class-level CACHE_TTL_MILLIS javadoc for
     * why a short TTL, not event-driven invalidation.
     */
    private CachedIdentity resolveIdentity(String clerkUserId, String clerkOrgId, String emailClaim, String platformRole) {
        String cacheKey = clerkUserId + "|" + (clerkOrgId == null ? "" : clerkOrgId);
        long now = clock.millis();

        CachedIdentity cached = identityCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached;
        }

        User user = resolveOrProvisionUser(clerkUserId, emailClaim);
        UUID resolvedTenantId = resolveTenantId(clerkOrgId, user, platformRole);
        // Deliberately NOT short-circuited on resolvedTenantId == null. Landlord
        // and renter are not mutually exclusive in this schema: tenant_profile is
        // keyed (tenant_id, clerk_user_id), so one person may rent from several
        // landlords AND separately run their own landlord org. Skipping this
        // lookup for org-bound users silently denied ROLE_TENANT to exactly those
        // dual-role people, which broke the whole renter portal for them.
        boolean isRenterProfile = tenantProfileRepository.existsByClerkUserId(clerkUserId);

        CachedIdentity fresh = new CachedIdentity(
                user.getId(), resolvedTenantId, user.getEmail(), user.getRole(), user.isActive(),
                isRenterProfile, now + CACHE_TTL_MILLIS
        );
        identityCache.put(cacheKey, fresh);
        sweepExpiredIfLarge(now);
        return fresh;
    }

    private void sweepExpiredIfLarge(long now) {
        if (identityCache.size() <= CACHE_SWEEP_THRESHOLD) {
            return;
        }
        identityCache.entrySet().removeIf(e -> e.getValue().expiresAtMillis() <= now);
    }

    private record CachedIdentity(
            UUID userId,
            UUID tenantId,
            String email,
            UserRole role,
            boolean active,
            boolean isRenterProfile,
            long expiresAtMillis
    ) {}

    /**
     * Coarse role (LANDLORD/TENANT/PENDING_ONBOARDING) is derived from
     * existing data, exactly as before. Within LANDLORD, a fine-grained
     * ROLE_LANDLORD_OWNER/_MANAGER/_STAFF authority is now also granted,
     * sourced from User.role — set either by the staff/manager invite flow
     * at User-creation time, or by first-user-of-tenant detection in
     * resolveTenantId() below for organic signups. ROLE_LANDLORD itself is
     * still granted alongside it for any guard that only cares "is this
     * any member of a landlord org," regardless of tier.
     *
     * NOTE: a renter with profiles under multiple landlords still gets a
     * single ROLE_TENANT grant here. Per-landlord/per-lease authorization
     * (e.g. "can only view their own lease") must be enforced separately at
     * the service/controller layer, not at this JWT-conversion stage.
     *
     * LANDLORD AND RENTER ARE ADDITIVE, NOT EXCLUSIVE. These used to be an
     * if/else chain, so a user bound to a landlord org could never receive
     * ROLE_TENANT even when they genuinely held a TenantProfile — which is a
     * real, supported state (a landlord who also rents somewhere). That made
     * every renter-portal endpoint 403 for them the moment those endpoints
     * gained an explicit ROLE_TENANT gate. Each authority is now granted on
     * its own merits, and PENDING_ONBOARDING is the fallback only when the
     * caller is genuinely neither.
     */
    private Set<SimpleGrantedAuthority> resolveAuthorities(UUID resolvedTenantId, UserRole role, boolean isRenterProfile) {

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        if (resolvedTenantId != null) {
            authorities.add(new SimpleGrantedAuthority(ROLE_LANDLORD));
            if (role != null) {
                authorities.add(new SimpleGrantedAuthority(toAuthority(role)));
            }
        }

        if (isRenterProfile) {
            authorities.add(new SimpleGrantedAuthority(ROLE_TENANT));
        }

        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PENDING_ONBOARDING));
        }

        return Set.copyOf(authorities);
    }

    private String toAuthority(UserRole role) {
        return switch (role) {
            case OWNER -> ROLE_LANDLORD_OWNER;
            case MANAGER -> ROLE_LANDLORD_MANAGER;
            case STAFF -> ROLE_LANDLORD_STAFF;
        };
    }

    /**
     * Grants platform-level authorities from the custom {@code platformRole}
     * claim — a Clerk user public-metadata value surfaced through the
     * "backend" JWT template. Deliberately independent of landlord org
     * membership: the platform owner/admin is NOT a member of every
     * landlord's Clerk organization, so authorization here must not depend
     * on tenant resolution.
     *
     * OWNER is a superset of ADMIN. Only OWNER may manage the most
     * sensitive platform configuration (credentials rotation, admin team),
     * enforced at the controller layer via hasAnyAuthority/hasAuthority.
     *
     * Existing landlord/renter/onboarding authorities are kept alongside —
     * a platform owner who also runs their own landlord account is both.
     */
    private Set<SimpleGrantedAuthority> withPlatformAuthorities(
            Set<SimpleGrantedAuthority> authorities,
            String platformRole
    ) {
        if (platformRole == null || platformRole.isBlank()) {
            return authorities;
        }

        Set<SimpleGrantedAuthority> combined = new HashSet<>(authorities);
        if (PLATFORM_ROLE_OWNER.equalsIgnoreCase(platformRole)) {
            combined.add(new SimpleGrantedAuthority(ROLE_PLATFORM_OWNER));
            combined.add(new SimpleGrantedAuthority(ROLE_PLATFORM_ADMIN));
        } else if (PLATFORM_ROLE_ADMIN.equalsIgnoreCase(platformRole)) {
            combined.add(new SimpleGrantedAuthority(ROLE_PLATFORM_ADMIN));
        }
        return Set.copyOf(combined);
    }

    /**
     * Just-in-time provisioning for users: a verified Clerk identity always
     * gets a corresponding local User row, created on first sight.
     *
     * Role is intentionally NOT set here. An invited staff/manager user
     * already exists by clerkUserId (created by the invite flow before
     * they ever logged in, via User.createInvited()) and hits the
     * `existing.isPresent()` branch below with role already populated.
     * Only a genuinely new/organic signup falls through to
     * User.createFromClerk(), with role resolved later in
     * resolveTenantId().
     */
    private User resolveOrProvisionUser(String clerkUserId, String email) {

        Optional<User> existing = userRepository.findByClerkUserId(clerkUserId);

        if (existing.isPresent()) {
            User user = existing.get();
            // Backfill. A user provisioned before the Clerk JWT template
            // carried an `email` claim is stamped PLACEHOLDER_EMAIL forever
            // otherwise — adding the claim later would fix nothing, because
            // this branch returned early without ever looking at it.
            //
            // Writes only on an actual change, so the common path stays a
            // pure read: this runs on every authenticated request, and an
            // unconditional save here would be a write per request.
            if (user.updateEmailIfChanged(email)) {
                userRepository.save(user);
            }
            return user;
        }

        User newUser = User.createFromClerk(clerkUserId, email != null ? email : PLACEHOLDER_EMAIL);
        return userRepository.save(newUser);
    }

    /**
     * Tenants are NOT auto-provisioned (deliberate business decision).
     * If no local Tenant exists for this Clerk org yet, we authenticate
     * the user with a null tenantId, so middleware/route guards can route
     * them to an explicit onboarding flow instead.
     *
     * First-user-of-tenant detection: the FIRST user ever linked to a given
     * tenantId is treated as its organic OWNER (e.g. the landlord who
     * signed up and created the org themselves). This check MUST run
     * before user.assignTenant(tenantId) — otherwise existsByTenantId()
     * would see this user's own about-to-be-linked row and always report
     * true, making every organic signup look like a non-first user.
     *
     * A user whose role is already set (invited via the staff/manager
     * invite flow) never has its role overwritten here, regardless of
     * first-user status.
     *
     * METADATA PROMOTION (authoritative writer): the moment a user becomes
     * bound to a landlord org — the renter/pending → landlord transition —
     * the backend writes publicMetadata.userType ("landlord", or "admin"
     * when the JWT also carries a platformRole claim, since admin is a
     * superset persona). This is the authoritative write the frontend
     * relies on (see ClerkService.setPublicMetadata javadoc + the frontend
     * writer contract). Best-effort: a Clerk sync failure must never break
     * authentication, so failures are logged, not thrown.
     */
    private UUID resolveTenantId(String clerkOrgId, User user, String platformRole) {

        if (clerkOrgId == null || clerkOrgId.isBlank()) {
            return null;
        }

        Optional<Tenant> tenant = tenantRepository.findByClerkOrgId(clerkOrgId);

        if (tenant.isEmpty()) {
            return null;
        }

        UUID tenantId = tenant.get().getId();
        boolean tenantLinkChanged = user.getTenantId() == null || !user.getTenantId().equals(tenantId);

        if (tenantLinkChanged) {

            boolean tenantAlreadyHasUsers = userRepository.existsByTenantId(tenantId);

            user.assignTenant(tenantId);

            if (user.getRole() == null) {
                // Conservative default for the (should-be-rare) case of a
                // non-first, non-invited user linking to an existing tenant
                // — e.g. added to the Clerk Org directly via Clerk's own
                // dashboard, bypassing our invite flow entirely: least
                // privilege (STAFF), never OWNER, unless genuinely first.
                user.assignRole(tenantAlreadyHasUsers ? UserRole.STAFF : UserRole.OWNER);
            }

            userRepository.save(user);
            promoteUserType(user, platformRole);
        }

        return tenantId;
    }

    /**
     * Backend-authoritative persona write at the tenant-binding transition.
     * A user who is also a platform admin keeps "admin" (superset persona —
     * the frontend routes admin+tenant dual holders to both trees). Everyone
     * else becomes "landlord". Best-effort: ClerkService is optional (missing
     * in @WebMvcTest slices, where the metadata write has no practical
     * effect), and even when present, a Clerk sync failure must never break
     * authentication.
     */
    private void promoteUserType(User user, String platformRole) {
        ClerkService clerkService = clerkServiceProvider.getIfAvailable();
        if (clerkService == null) {
            return;
        }
        boolean isPlatformAdmin = PLATFORM_ROLE_OWNER.equalsIgnoreCase(platformRole)
                || PLATFORM_ROLE_ADMIN.equalsIgnoreCase(platformRole);
        String userType = isPlatformAdmin ? "admin" : "landlord";
        clerkService.setPublicMetadata(user.getClerkUserId(), Map.of(ClerkService.USER_TYPE_KEY, userType));
    }
}