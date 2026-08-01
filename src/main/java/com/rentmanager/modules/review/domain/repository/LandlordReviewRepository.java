package com.rentmanager.modules.review.domain.repository;

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
}
