package com.rentmanager.modules.review.application.dto.response;

import java.time.Instant;

/**
 * Public-facing review item (Phase 4b): renter identity is deliberately
 * redacted to a first name on public routes, and no internal ids are
 * exposed. The landlord dashboard continues to use the full
 * {@link LandlordReviewResponse}.
 */
public record PublicLandlordReviewResponse(
        int rating,
        String renterName,
        String comment,
        Instant createdAt
) {
}
