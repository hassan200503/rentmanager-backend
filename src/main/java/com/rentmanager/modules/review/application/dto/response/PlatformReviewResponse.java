package com.rentmanager.modules.review.application.dto.response;

import com.rentmanager.modules.review.application.ReviewModerationService;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in the platform moderation queue. {@code reviewerName} is a
 * full name — this record is only ever exposed to platform admins.
 */
public record PlatformReviewResponse(
        ReviewModerationService.ReviewType type,
        UUID reviewId,
        String reviewerName,
        int rating,
        String comment,
        ReviewStatus status,
        Instant createdAt
) {
}