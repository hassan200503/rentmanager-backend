package com.rentmanager.modules.identity.clerk;

public interface ClerkService {

    /**
     * Creates a tenant user in external identity system.
     * Must be idempotent using email/phone uniqueness.
     */
    String createTenantUser(String fullName, String email, String phone, String password);

    /**
     * Checks whether a Clerk user already exists for this email, without
     * creating one. Used by callers (e.g. the fulfillment saga) that need to
     * know in advance whether createTenantUser will create a NEW account or
     * reuse an existing one — needed to decide whether compensation should
     * delete the account on failure.
     */
    boolean existsByEmail(String email);

    /**
     * Deletes a Clerk user by id. Used only as a compensating action when a
     * fulfillment saga fails after a NEW Clerk account was created this run.
     * Never called for accounts that were reused from a prior reservation.
     */
    void deleteUser(String clerkUserId);
}