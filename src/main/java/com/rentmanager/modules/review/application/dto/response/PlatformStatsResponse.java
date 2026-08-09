package com.rentmanager.modules.review.application.dto.response;

/**
 * Platform-wide moderation stats (V65/V66): how many reviews exist per
 * direction and moderation state, plus the average rating of the
 * published (approved) content on each side. {@code platform*} counts
 * reviews of the platform itself (V66) — the testimonials source.
 */
public record PlatformStatsResponse(
        long landlordApproved,
        long landlordPending,
        long landlordHidden,
        double landlordAverageRating,
        long renterApproved,
        long renterPending,
        long renterHidden,
        double renterAverageRating,
        long platformApproved,
        long platformPending,
        long platformHidden,
        double platformAverageRating
) {
}