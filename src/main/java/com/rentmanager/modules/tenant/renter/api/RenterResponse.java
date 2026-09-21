package com.rentmanager.modules.tenant.renter.api;

import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;

import java.util.UUID;

/**
 * A renter as their landlord sees them.
 *
 * Deliberately omits the Clerk user id: the landlord has no use for another
 * person's identity provider id, and {@code hasAccount} is the only part of it
 * that means anything to them ("can this person sign in and see their rent?").
 */
public record RenterResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        String nationalId,
        boolean hasAccount
) {
    public static RenterResponse from(TenantProfile profile) {
        return new RenterResponse(
                profile.getId(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getEmail(),
                profile.getNationalId(),
                !profile.isUnlinked()
        );
    }
}
