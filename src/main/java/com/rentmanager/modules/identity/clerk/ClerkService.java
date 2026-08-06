package com.rentmanager.modules.identity.clerk;

import java.util.Map;

public interface ClerkService {

    /**
     * Canonical publicMetadata key for the persona. The authoritative driver
     * is the Java backend: it writes this value at every state transition
     * (see the writer contract in the frontend's lib/auth/clerk-metadata.ts).
     * The frontend webhook only seeds it when ABSENT, so backend writes always
     * win. Never both string here and allow it to drift.
     */
    String USER_TYPE_KEY = "userType";

    /**
     * Creates a tenant user in the external identity system, or reuses an
     * existing one if the email is already registered. Idempotent by design.
     * Tenant users are created WITHOUT a password — authentication is
     * handled via single-use sign-in tokens.
     *
     * The returned newlyCreated flag reflects what THIS call actually did —
     * it is the single source of truth for whether the account is new.
     * Callers should rely on this flag rather than a separate pre-check
     * against existsByEmail, since a check-then-create across two calls
     * is inherently racy.
     */
    ClerkUserCreationResult createTenantUser(String fullName, String email, String phone);

    /**
     * Creates a landlord-org staff/manager user in the external identity
     * system. Deliberately a SEPARATE method from createTenantUser, even
     * though the underlying Clerk API call is identical — this keeps the
     * reservation-fulfillment saga's compensation logic (which depends on
     * createTenantUser's exact call sites and newlyCreated() semantics)
     * completely isolated from the staff-invite flow. A change to one
     * must never risk the other.
     *
     * Unlike createTenantUser, callers of this method should treat
     * newlyCreated() == false as a hard rejection, not a reuse path —
     * there is no safe way to deliver credentials for an account that
     * already exists.
     */
    ClerkUserCreationResult createStaffUser(String fullName, String email, String phone, String password);

    /**
     * Creates a single-use sign-in token for the given Clerk user,
     * valid for expiresInSeconds. The returned SignInTokenResult
     * contains the url that should be delivered to the user (e.g. via SMS).
     */
    SignInTokenResult createSignInToken(String clerkUserId, int expiresInSeconds);

    /**
     * Checks whether a Clerk user already exists for this email, without
     * creating one. General-purpose existence check. Callers that need to
     * know whether createTenantUser will create vs reuse an account should
     * use ClerkUserCreationResult.newlyCreated() instead of this method,
     * to avoid a check-then-create race.
     */
    boolean existsByEmail(String email);

    /**
     * Deletes a Clerk user by id. Used as a compensating action when
     * account creation succeeds in Clerk but the corresponding local
     * write fails afterward (reservation saga, or staff-invite flow).
     * Never called for accounts that were reused rather than newly created.
     */
    void deleteUser(String clerkUserId);

    /**
     * Writes public_metadata for the given Clerk user. The caller supplies
     * a flat map of keys to values — the backend is the AUTHORITATIVE writer
     * of personas (see {@link #USER_TYPE_KEY}), and this method is how it
     * persists a persona change (renter ↔ landlord_pending ↔ landlord). The
     * frontend webhook may also seed a value, but it only ever does so when
     * the key is absent, so a write here can never be stomped by a replayed
     * webhook event.
     *
     * <p>Implementations must tolerate transient Clerk API failures without
     * throwing into the caller's business transaction (callers log + move
     * on, don't roll back a tenant/provisioning change because the metadata
     * sync hiccuped). PATCH /users/{id}/metadata with {"public_metadata": {...}}
     * (the dedicated metadata endpoint; public_metadata on PATCH /users/{id}
     * itself is deprecated by Clerk).
     */
    void setPublicMetadata(String clerkUserId, Map<String, String> metadata);
}