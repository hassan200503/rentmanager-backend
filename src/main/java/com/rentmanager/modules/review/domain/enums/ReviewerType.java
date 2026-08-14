package com.rentmanager.modules.review.domain.enums;

/**
 * The side of the platform a platform-review author belongs to (V69).
 * Landlords hold a tenant link ({@code users.tenant_id}); renters are
 * identified by a {@code tenant_profile} row. Snapshot at write time so
 * the public testimonials wall can label each reviewer as a landlord or
 * a renter — two distinct audiences with their own opinions of the
 * platform.
 */
public enum ReviewerType {
    LANDLORD,
    RENTER
}
