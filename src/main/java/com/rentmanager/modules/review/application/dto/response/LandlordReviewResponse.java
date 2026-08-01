package com.rentmanager.modules.review.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record LandlordReviewResponse(
        UUID id,
        String renterName,
        int rating,
        String comment,
        Instant createdAt
) {
}
