package com.rentmanager.modules.identity.clerk;

public interface ClerkService {

    /**
     * Creates a tenant user in the external identity system, or reuses an
     * existing one if the email is already registered. Idempotent by design.
     *
     * The returned newlyCreated flag reflects what THIS call actually did —
     * it is the single source of truth for whether the account is new.
     * Callers should rely on this flag rather than a separate pre-check
     * against existsByEmail, since a check-then-create across two calls
     * is inherently racy.
     */
    ClerkUserCreationResult createTenantUser(String fullName, String email, String phone, String password);

    /**
     * Checks whether a Clerk user already exists for this email, without
     * creating one. General-purpose existence check. Callers that need to
     * know whether createTenantUser will create vs reuse an account should
     * use ClerkUserCreationResult.newlyCreated() instead of this method,
     * to avoid a check-then-create race.
     */
    boolean existsByEmail(String email);

    /**
     * Deletes a Clerk user by id. Used only as a compensating action when a
     * fulfillment saga fails after a NEW Clerk account was created this run.
     * Never called for accounts that were reused from a prior reservation.
     */
    void deleteUser(String clerkUserId);
}