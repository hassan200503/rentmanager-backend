package com.rentmanager.modules.review.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Renter review submission. Deliberately no leaseId/tenantId in the body:
 * the calling layer (TenantPortalService) resolves the landlord tenant and
 * the renter's verifiable lease server-side, so cross-tenant submission is
 * structurally impossible.
 */
public record SubmitReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 1000) String comment
) {
}
