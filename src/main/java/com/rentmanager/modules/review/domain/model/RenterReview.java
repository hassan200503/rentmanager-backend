package com.rentmanager.modules.review.domain.model;

import com.rentmanager.domain.base.AggregateRoot;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A landlord's verified review of a renter (V65, bidirectional platform
 * ratings).
 *
 * <p>Same trust rules as {@link LandlordReview}, mirrored: the landlord
 * must have (or have had) an active lease with the renter being reviewed.
 * {@code tenantId} is the landlord's own account — tenant isolation stays
 * intact — and the reviewed party is identified by
 * {@code tenantProfileId}. New reviews are created {@code PENDING}; only
 * {@code APPROVED} reviews reach public surfaces (platform testimonials).</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RenterReview extends AggregateRoot {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 1000;

    private UUID tenantProfileId;
    private UUID leaseId;
    private int rating;
    private String comment;
    private ReviewStatus status;

    public static RenterReview submit(
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

        RenterReview review = new RenterReview();
        review.assignTenant(landlordTenantId);
        review.tenantProfileId = tenantProfileId;
        review.leaseId = leaseId;
        review.rating = rating;
        review.comment = normalizeComment(comment);
        review.status = ReviewStatus.PENDING;
        return review;
    }

    /**
     * Publishes the review once platform moderation approves it. Pending
     * reviews are approved normally; a previously hidden review can be
     * restored to the public feed with an explicit re-approval.
     *
     * @throws IllegalStateException if the review is already approved.
     */
    public void approve() {
        if (status != ReviewStatus.PENDING && status != ReviewStatus.HIDDEN) {
            throw new IllegalStateException(
                    "Only a pending or hidden review can be approved (current: " + status + ")");
        }
        this.status = ReviewStatus.APPROVED;
    }

    /**
     * Removes the review from all public surfaces. Pending or approved
     * reviews can be hidden; an already-hidden review stays hidden.
     */
    public void hide() {
        if (status == ReviewStatus.HIDDEN) {
            throw new IllegalStateException("Review is already hidden");
        }
        this.status = ReviewStatus.HIDDEN;
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    public static RenterReview rehydrate(
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
        return rehydrate(id, landlordTenantId, tenantProfileId, leaseId, rating, comment,
                ReviewStatus.APPROVED, version, createdAt, updatedAt);
    }

    public static RenterReview rehydrate(
            UUID id,
            UUID landlordTenantId,
            UUID tenantProfileId,
            UUID leaseId,
            int rating,
            String comment,
            ReviewStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        RenterReview review = new RenterReview();
        review.setId(id);
        review.assignTenant(landlordTenantId);
        review.tenantProfileId = tenantProfileId;
        review.leaseId = leaseId;
        review.rating = rating;
        review.comment = comment;
        review.status = status != null ? status : ReviewStatus.PENDING;
        review.setVersion(version);
        review.restoreCreatedAt(createdAt);
        review.restoreUpdatedAt(updatedAt);
        return review;
    }
}