package com.rentmanager.modules.review.infrastructure.persistence.repository;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.infrastructure.persistence.entity.LandlordReviewJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LandlordReviewJpaRepository extends JpaRepository<LandlordReviewJpaEntity, UUID> {

    List<LandlordReviewJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID landlordTenantId);

    Optional<LandlordReviewJpaEntity> findByTenantIdAndTenantProfileId(
            UUID landlordTenantId, UUID tenantProfileId);

    long countByTenantId(UUID landlordTenantId);

    List<LandlordReviewJpaEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID landlordTenantId, ReviewStatus status);

    long countByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status);

    long countByStatus(ReviewStatus status);

    List<LandlordReviewJpaEntity> findByStatusOrderByCreatedAtDesc(
            ReviewStatus status, Pageable pageable);

    @Query("select avg(l.rating) from LandlordReviewJpaEntity l where l.status = :status")
    Optional<Double> findAverageRatingByStatus(@Param("status") ReviewStatus status);
}