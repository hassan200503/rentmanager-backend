package com.rentmanager.modules.review.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Landlord -> renter review submission (V65). The tenant id and the
 * reviewed lease are resolved server-side from the authenticated
 * landlord's account; only the renter profile is client-supplied.
 */
public record SubmitRenterReviewRequest(
        @NotNull(message = "Renter profile is required")
        UUID tenantProfileId,

        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        int rating,

        @Size(max = 1000, message = "Comment must be at most 1000 characters")
        String comment
) {
}