package com.rentmanager.modules.review.application.dto.response;

/**
 * Review summary for a landlord. {@code averageRating} is null (and
 * {@code averageShown} false) until at least 3 reviews exist - a trust
 * average built on one or two reviews is deliberately never exposed.
 */
public record ReviewSummaryResponse(
        int reviewCount,
        Double averageRating,
        boolean averageShown
) {
}
