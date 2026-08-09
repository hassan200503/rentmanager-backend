package com.rentmanager.modules.review.application.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the public platform testimonials feed (V65). Reviewer
 * names are always redacted to the first name on this surface.
 *
 * @param reviewerFirstName the reviewer's first name, or null if the
 *                          profile could not be resolved
 */
public record PlatformTestimonialResponse(
        UUID reviewId,
        String reviewerFirstName,
        int rating,
        String comment,
        Instant createdAt
) {
}