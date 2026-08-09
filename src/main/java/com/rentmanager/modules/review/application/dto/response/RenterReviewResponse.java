package com.rentmanager.modules.review.application.dto.response;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A landlord's review of a renter (V65). {@code renterName} is the full
 * name of the renter profile being reviewed; the landlord's dashboard
 * sees status badges, the renter-facing surface only ever receives
 * {@code APPROVED} reviews.
 */
public record RenterReviewResponse(
        UUID id,
        String renterName,
        int rating,
        String comment,
        ReviewStatus status,
        Instant createdAt
) {
}