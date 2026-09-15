package com.rentmanager.modules.user.application.dto.response;

import java.util.UUID;

/**
 * What the backend will actually authorise for the calling session, derived
 * from the same authorities {@code @PreAuthorize} evaluates.
 *
 * Exists so clients (the mobile app first) choose which experience to show
 * from the authority source of truth, instead of re-deriving a persona from
 * JWT claims or Clerk metadata that can lag the database. It is a UI hint
 * only: every endpoint still enforces its own gate.
 *
 * @param landlordRole      OWNER | MANAGER | STAFF when bound to a landlord
 *                          organisation, otherwise null
 * @param landlordTenantId  the landlord organisation ({@code tenants} row —
 *                          NOT a renter), or null
 * @param renter            the caller holds at least one renter profile
 * @param pendingOnboarding authenticated but neither landlord nor renter
 * @param platformAdmin     carries a platform admin authority
 */
public record SessionAccessResponse(
        UUID userId,
        String landlordRole,
        UUID landlordTenantId,
        boolean renter,
        boolean pendingOnboarding,
        boolean platformAdmin
) {}
