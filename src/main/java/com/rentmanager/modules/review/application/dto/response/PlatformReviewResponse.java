package com.rentmanager.modules.review.application.dto.response;

import com.rentmanager.modules.review.application.ReviewModerationService;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.enums.ReviewerType;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the platform moderation queue. {@code reviewerName} is a
 * full name — this record is only ever exposed to platform admins.
 *
 * <p>{@code reviewerType} (V69) is only populated for {@code PLATFORM}
 * reviews (LANDLORD or RENTER — the author's side of the platform).
 * Direction reviews (renter → landlord / landlord → renter) have no
 * reviewer side of their own, so it is {@code null} for them.</p>
 */
public record PlatformReviewResponse(
        ReviewModerationService.ReviewType type,
        UUID reviewId,
        String reviewerName,
        ReviewerType reviewerType,
        int rating,
        String comment,
        ReviewStatus status,
        Instant createdAt
) {
}