package com.rentmanager.modules.review.domain.repository;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.RenterReview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RenterReviewRepository {

    RenterReview save(RenterReview review);

    Optional<RenterReview> findById(UUID id);

    List<RenterReview> findByTenantId(UUID landlordTenantId);

    Optional<RenterReview> findByTenantIdAndTenantProfileId(
            UUID landlordTenantId, UUID tenantProfileId);

    List<RenterReview> findByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status);

    long countByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status);

    /**
     * Only published reviews — the public surfaces (platform testimonials,
     * renter profiles) must never see pending or hidden content.
     */
    List<RenterReview> findApprovedByTenantId(UUID landlordTenantId);

    long countApprovedByTenantId(UUID landlordTenantId);
}