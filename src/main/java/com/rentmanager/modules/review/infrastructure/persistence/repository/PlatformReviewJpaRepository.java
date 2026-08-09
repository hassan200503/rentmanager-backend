package com.rentmanager.modules.review.infrastructure.persistence.repository;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.infrastructure.persistence.entity.PlatformReviewJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformReviewJpaRepository extends JpaRepository<PlatformReviewJpaEntity, UUID> {

    Optional<PlatformReviewJpaEntity> findByReviewerUserId(UUID reviewerUserId);

    List<PlatformReviewJpaEntity> findByStatusOrderByCreatedAtDesc(
            ReviewStatus status, Pageable pageable);

    long countByStatus(ReviewStatus status);

    @Query("select avg(r.rating) from PlatformReviewJpaEntity r where r.status = :status")
    Optional<Double> findAverageRatingByStatus(@Param("status") ReviewStatus status);
}