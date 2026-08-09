package com.rentmanager.modules.review.infrastructure.persistence.repository;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.infrastructure.persistence.entity.RenterReviewJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only repository for {@code renter_reviews}, kept deliberately thin.
 *
 * <p>Write access goes through the domain repository (
 * {@code RenterReviewRepository} + adapter) so domain invariants can never
 * be bypassed. {@link #save} is intentionally not exposed here.</p>
 */
public interface RenterReviewJpaRepository extends JpaRepository<RenterReviewJpaEntity, UUID> {

    List<RenterReviewJpaEntity> findByTenantIdOrderByCreatedAtDesc(UUID landlordTenantId);

    Optional<RenterReviewJpaEntity> findByTenantIdAndTenantProfileId(
            UUID landlordTenantId, UUID tenantProfileId);

    long countByTenantId(UUID landlordTenantId);

    List<RenterReviewJpaEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
            UUID landlordTenantId, ReviewStatus status);

    long countByTenantIdAndStatus(UUID landlordTenantId, ReviewStatus status);

    long countByStatus(ReviewStatus status);

    List<RenterReviewJpaEntity> findByStatusOrderByCreatedAtDesc(
            ReviewStatus status, Pageable pageable);

    @Query("select avg(r.rating) from RenterReviewJpaEntity r where r.status = :status")
    Optional<Double> findAverageRatingByStatus(@Param("status") ReviewStatus status);
}