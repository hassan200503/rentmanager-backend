package com.rentmanager.modules.review.domain.repository;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.LandlordReview;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LandlordReviewRepository {

    LandlordReview save(LandlordReview review);

    Optional<LandlordReview> findById(UUID id);

    List<LandlordReview> findByTenantId(UUID landlordTenantId);

    Optional<LandlordReview> findByTenantIdAndTenantProfileId(
            UUID landlordTenantId, UUID tenantProfileId);

    long countByTenantId(UUID landlordTenantId);

    /**
     * Reviews in a given moderation state for one landlord's dashboard.
     */
    List<LandlordReview> findByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status);

    long countByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status);

    /**
     * Only published reviews — the public surfaces (listing pages, platform
     * testimonials) must never see pending or hidden content.
     */
    List<LandlordReview> findApprovedByTenantId(UUID landlordTenantId);

    long countApprovedByTenantId(UUID landlordTenantId);
}