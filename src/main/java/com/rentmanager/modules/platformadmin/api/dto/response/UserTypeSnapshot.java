package com.rentmanager.modules.platformadmin.api.dto.response;

/**
 * Identity snapshot row for the one-time userType backfill (frontend
 * scripts/migrate-user-types.ts). One row per known identity, mapping the
 * local database truth to the canonical Clerk publicMetadata.userType value.
 *
 * Deliberately NOT paginated: the frontend migration script performs a
 * full-database classification and runs Server-side (ops tooling, platform
 * admin only — @PreAuthorize on the controller). Admin identities have no
 * DB row (platformRole is a claim, not persisted state), so they are absent
 * here by design; the script's own clerk-user enumeration covers them.
 */
public record UserTypeSnapshot(
        String clerkUserId,
        String userType
) {
}