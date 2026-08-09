package com.rentmanager.modules.review.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Platform review submission (V66): a user rates the platform itself.
 * Deliberately no reviewer identity in the body — it always comes from
 * the authenticated principal.
 */
public record SubmitPlatformReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String comment
) {
}