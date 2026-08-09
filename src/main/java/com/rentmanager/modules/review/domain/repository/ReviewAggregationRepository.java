package com.rentmanager.modules.review.domain.repository;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.domain.model.RenterReview;

import java.util.List;
import java.util.Optional;

/**
 * Cross-tenant read facade over every review kind (V65: landlord +
 * renter; V66: platform reviews), deliberately separated from the
 * tenant-scoped repositories.
 *
 * <p>Every method here crosses tenant boundaries by design and is only
 * wired into the platform administration surface and the public
 * testimonials endpoint — it must never be reachable through a
 * landlord/renter-scoped service.</p>
 */
public interface ReviewAggregationRepository {

    List<LandlordReview> findLandlordReviews(ReviewStatus status, int limit);

    List<RenterReview> findRenterReviews(ReviewStatus status, int limit);

    List<PlatformReview> findPlatformReviews(ReviewStatus status, int limit);

    long countLandlordReviews(ReviewStatus status);

    long countRenterReviews(ReviewStatus status);

    long countPlatformReviews(ReviewStatus status);

    Optional<Double> averageLandlordRating(ReviewStatus status);

    Optional<Double> averageRenterRating(ReviewStatus status);

    Optional<Double> averagePlatformRating(ReviewStatus status);
}