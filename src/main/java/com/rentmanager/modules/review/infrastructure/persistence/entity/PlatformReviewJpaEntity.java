package com.rentmanager.modules.review.infrastructure.persistence.entity;

import com.rentmanager.domain.base.BaseEntity;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.enums.ReviewerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Persistence mirror of {@code PlatformReview}. Deliberately extends
 * {@code BaseEntity} (not {@code BaseTenantEntity}): platform reviews
 * have no {@code tenant_id} — they are cross-tenant by nature.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "platform_reviews",
        indexes = {
                @Index(name = "idx_platform_reviews_status_created", columnList = "status, created_at"),
                @Index(name = "idx_platform_reviews_reviewer", columnList = "reviewer_user_id")
        }
)
public class PlatformReviewJpaEntity extends BaseEntity {

    @Column(name = "reviewer_user_id", nullable = false, unique = true)
    private UUID reviewerUserId;

    @Column(name = "reviewer_name", nullable = false, length = 100)
    private String reviewerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_type", nullable = false, length = 30)
    private ReviewerType reviewerType;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReviewStatus status = ReviewStatus.PENDING;
}