package com.rentmanager.modules.review.domain.model;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.enums.ReviewerType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's verified review of the platform itself (V66) — the true
 * source of the public testimonials feed.
 *
 * <p>A platform review is tenant-less by design: it rates RentManager,
 * not any landlord account, so it deliberately does not extend
 * {@code AggregateRoot}/{@code BaseTenantEntity}. Identity and lifecycle
 * come from {@code BaseEntity} instead.</p>
 *
 * <p>One review per user ({@code uq_platform_reviews_reviewer}): a first
 * submission creates the row {@code PENDING}; any later submission from
 * the same user edits the existing review in place via
 * {@link #replace}. Every edit sends the review back to {@code PENDING}
 * so edited content can never bypass platform moderation.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformReview extends BaseEntity {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 1000;

    /** Sentinal display name for reviewers without a profile name. Never
     * exposes an email address on the public testimonials wall. */
    public static final String DEFAULT_REVIEWER_NAME = "Verified user";

    private UUID reviewerUserId;
    private String reviewerName;
    private ReviewerType reviewerType;
    private int rating;
    private String comment;
    private ReviewStatus status;

    public static PlatformReview submit(
            UUID reviewerUserId,
            String reviewerName,
            ReviewerType reviewerType,
            int rating,
            String comment
    ) {
        if (reviewerUserId == null) {
            throw new IllegalArgumentException("Reviewer is required");
        }
        if (reviewerType == null) {
            throw new IllegalArgumentException("Reviewer type is required");
        }
        String displayName = (reviewerName == null || reviewerName.isBlank())
                ? DEFAULT_REVIEWER_NAME
                : reviewerName.trim();
        validateRating(rating);
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }

        PlatformReview review = new PlatformReview();
        review.reviewerUserId = reviewerUserId;
        review.reviewerName = displayName;
        review.reviewerType = reviewerType;
        review.rating = rating;
        review.comment = normalizeComment(comment);
        review.status = ReviewStatus.PENDING;
        return review;
    }

    /**
     * In-place edit of an existing review (gated by the unique
     * reviewer_user_id). The review always re-enters {@code PENDING}:
     * an approved review that is edited must be moderated again before
     * its new content reaches the public testimonials feed. A hidden
     * review can also be edited this way — the user's rewrite is a fresh
     * moderation decision instead of a dead end.
     */
    public void replace(int rating, String comment) {
        validateRating(rating);
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "Comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }
        this.rating = rating;
        this.comment = normalizeComment(comment);
        this.status = ReviewStatus.PENDING;
    }

    private static void validateRating(int rating) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new IllegalArgumentException(
                    "Rating must be between " + MIN_RATING + " and " + MAX_RATING);
        }
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
     * Removes the review from all public surfaces. Approved or pending
     * reviews can be hidden; hidden reviews stay hidden.
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

    public static PlatformReview rehydrate(
            UUID id,
            UUID reviewerUserId,
            String reviewerName,
            ReviewerType reviewerType,
            int rating,
            String comment,
            ReviewStatus status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        PlatformReview review = new PlatformReview();
        review.setId(id);
        review.reviewerUserId = reviewerUserId;
        review.reviewerName = reviewerName;
        review.reviewerType = reviewerType;
        review.rating = rating;
        review.comment = comment;
        review.status = status != null ? status : ReviewStatus.PENDING;
        review.setVersion(version);
        review.restoreCreatedAt(createdAt);
        review.restoreUpdatedAt(updatedAt);
        return review;
    }
}