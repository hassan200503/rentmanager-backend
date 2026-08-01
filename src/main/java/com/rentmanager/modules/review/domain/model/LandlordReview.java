package com.rentmanager.modules.review.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A renter's verified review of a landlord (Phase 4b).
 *
 * <p>Verification is enforced at the application layer ({@code
 * ReviewCommandService}): a review is only valid from a renter who has or
 * had an active lease with the landlord. Tenant isolation is inherited
 * from {@code AggregateRoot} - the tenantId is always the landlord's
 * account, assigned once via {@code assignTenant} and never client-
 * supplied.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LandlordReview extends AggregateRoot {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 1000;

    private UUID tenantProfileId;
    private UUID leaseId;
    private int rating;
    private String comment;

    public static LandlordReview submit(
            UUID landlordTenantId,
            UUID tenantProfileId,
            UUID leaseId,
            int rating,
            String comment
    ) {
        if (tenantProfileId == null) {
            throw new IllegalArgumentException("Renter profile is required");
        }
        if (leaseId == null) {
            throw new IllegalArgumentException("Lease is required");
        }
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                    "Rating must be between " + MIN_RATING + " and " + MAX_RATING);
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }

        LandlordReview review = new LandlordReview();
        review.assignTenant(landlordTenantId);
        review.tenantProfileId = tenantProfileId;
        review.leaseId = leaseId;
        review.rating = rating;
        review.comment = normalizeComment(comment);
        return review;
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    public static LandlordReview rehydrate(
            UUID id,
            UUID landlordTenantId,
            UUID tenantProfileId,
            UUID leaseId,
            int rating,
            String comment,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        LandlordReview review = new LandlordReview();
        review.setId(id);
        review.assignTenant(landlordTenantId);
        review.tenantProfileId = tenantProfileId;
        review.leaseId = leaseId;
        review.rating = rating;
        review.comment = comment;
        review.setVersion(version);
        review.restoreCreatedAt(createdAt);
        review.restoreUpdatedAt(updatedAt);
        return review;
    }
}
