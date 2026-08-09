package com.rentmanager.modules.review.domain.repository;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.PlatformReview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Write and query access to platform reviews (users rating the platform
 * itself, V66). Distinct from the cross-tenant aggregation facade
 * ({@code ReviewAggregationRepository}).
 *
 * <p>Writes go through this domain repository only so {@code
 * PlatformReview} invariants (one review per user, re-moderation on
 * edit) can never be bypassed.</p>
 */
public interface PlatformReviewRepository {

    PlatformReview save(PlatformReview review);

    Optional<PlatformReview> findById(UUID id);

    Optional<PlatformReview> findByReviewerUserId(UUID reviewerUserId);

    List<PlatformReview> findByStatusOrderByCreatedAtDesc(ReviewStatus status, int limit);

    long countByStatus(ReviewStatus status);

    Optional<Double> averageRatingByStatus(ReviewStatus status);
}