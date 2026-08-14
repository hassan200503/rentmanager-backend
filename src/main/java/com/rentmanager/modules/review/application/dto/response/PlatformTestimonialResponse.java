package com.rentmanager.modules.review.application.dto.response;

import com.rentmanager.modules.review.domain.enums.ReviewerType;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the public platform testimonials feed (V65/V69). Reviewer
 * names are always redacted to the first name on this surface, and the
 * reviewer's side ({@code LANDLORD} or {@code RENTER}) is snapshotted so
 * the wall can label each quote with the reviewer's role.
 *
 * @param reviewerFirstName the reviewer's first name, or null if the
 *                          profile could not be resolved
 * @param reviewerType      the reviewer's side: LANDLORD or RENTER
 */
public record PlatformTestimonialResponse(
        UUID reviewId,
        String reviewerFirstName,
        ReviewerType reviewerType,
        int rating,
        String comment,
        Instant createdAt
) {
}