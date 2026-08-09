package com.rentmanager.modules.review.application.dto.response;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record LandlordReviewResponse(
        UUID id,
        String renterName,
        int rating,
        String comment,
        ReviewStatus status,
        Instant createdAt
) {
}