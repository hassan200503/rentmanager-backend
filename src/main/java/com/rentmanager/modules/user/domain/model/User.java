package com.rentmanager.modules.user.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private String clerkUserId;
    private UUID tenantId;
    private String email;
    private String firstName;
    private String lastName;
    private boolean active;
    private UserRole role;

    private User(String clerkUserId, String email) {
        this.clerkUserId = clerkUserId;
        this.email = email;
        this.active = true;
    }

    private User(
            String clerkUserId,
            String email,
            String firstName,
            String lastName,
            UUID tenantId,
            UserRole role
    ) {
        this.clerkUserId = clerkUserId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.tenantId = tenantId;
        this.role = role;
        this.active = true;
    }

    /**
     * Just-in-time provisioning path (see ClerkJwtAuthenticationConverter).
     * Role is intentionally left null here — it is resolved later, either
     * by first-user-of-tenant detection (OWNER) when a tenant link is
     * first established, or was already set via createInvited() if this
     * Clerk identity was provisioned through the staff/manager invite flow
     * (in which case resolveOrProvisionUser() finds this row by
     * clerkUserId before ever reaching this factory).
     */
    public static User createFromClerk(String clerkUserId, String email) {
        if (clerkUserId == null || clerkUserId.isBlank()) {
            throw new IllegalArgumentException("Clerk user ID is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return new User(clerkUserId, email);
    }

    /**
     * Invite-flow path: tenantId and role are known and set at creation
     * time, before the invited person ever logs in. This means the
     * JIT-provisioning path in ClerkJwtAuthenticationConverter never has
     * to guess a role for an invited user — findByClerkUserId() finds this
     * row already fully formed on their first login.
     */
    public static User createInvited(
            String clerkUserId,
            String email,
            String firstName,
            String lastName,
            UUID tenantId,
            UserRole role
    ) {
        if (clerkUserId == null || clerkUserId.isBlank()) {
            throw new IllegalArgumentException("Clerk user ID is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID is required for an invited user");
        }
        if (role == null) {
            throw new IllegalArgumentException("Role is required for an invited user");
        }
        return new User(clerkUserId, email, firstName, lastName, tenantId, role);
    }

    public static User rehydrate(
            UUID id,
            Long version,
            String clerkUserId,
            UUID tenantId,
            String email,
            String firstName,
            String lastName,
            boolean active,
            UserRole role
    ) {
        User user = new User();
        user.setId(id);
        user.setVersion(version);
        user.clerkUserId = clerkUserId;
        user.tenantId = tenantId;
        user.email = email;
        user.firstName = firstName;
        user.lastName = lastName;
        user.active = active;
        user.role = role;
        return user;
    }

    public void assignTenant(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        this.tenantId = tenantId;
    }

    /**
     * Sets this user's role within their tenant. Used by
     * ClerkJwtAuthenticationConverter for first-user-of-tenant OWNER
     * assignment on organic signups, and available for future role-change
     * flows (an OWNER promoting/demoting a MANAGER/STAFF member).
     */
    public void assignRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        this.role = role;
    }

    /**
     * Records the email address carried by a later Clerk token.
     *
     * <p>A user provisioned from a token with no {@code email} claim is
     * stamped with the {@code unknown@clerk.user} placeholder. Nothing used
     * to correct that, so the placeholder was permanent — and because it is
     * a non-blank string it passes every {@code != null && !isBlank()} guard
     * in the notification code, which then posts to a mailbox that does not
     * exist. A null would have been skipped visibly; the placeholder failed
     * silently.
     *
     * @return true when the stored address actually changed
     */
    /**
     * Syncs first and last name received from a Clerk webhook or JWT claim.
     * Returns true when at least one field actually changed.
     */
    public boolean updateNameIfChanged(String firstName, String lastName) {
        String incomingFirst  = (firstName  != null) ? firstName.trim()  : "";
        String incomingLast   = (lastName   != null) ? lastName.trim()   : "";
        String existingFirst  = (this.firstName  != null) ? this.firstName.trim()  : "";
        String existingLast   = (this.lastName   != null) ? this.lastName.trim()   : "";

        boolean changed = !incomingFirst.equalsIgnoreCase(existingFirst)
                || !incomingLast.equalsIgnoreCase(existingLast);

        if (changed) {
            if (!incomingFirst.isEmpty()) this.firstName = incomingFirst;
            if (!incomingLast.isEmpty())  this.lastName  = incomingLast;
        }
        return changed;
    }

    public boolean updateEmailIfChanged(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String incoming = email.trim();
        if (incoming.equalsIgnoreCase(this.email)) {
            return false;
        }
        this.email = incoming;
        return true;
    }

    public boolean hasRole(UserRole candidate) {
        return this.role == candidate;
    }
}