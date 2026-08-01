package com.rentmanager.modules.review.application.dto.response;

import java.util.List;

/**
 * Public landlord review payload for listing pages. Honesty rule shared
 * with {@link ReviewSummaryResponse}: {@code averageRating} stays null
 * (and {@code averageShown} false) until at least 3 reviews exist.
 */
public record PublicLandlordReviewsResponse(
        int reviewCount,
        Double averageRating,
        boolean averageShown,
        List<PublicLandlordReviewResponse> reviews
) {
}
