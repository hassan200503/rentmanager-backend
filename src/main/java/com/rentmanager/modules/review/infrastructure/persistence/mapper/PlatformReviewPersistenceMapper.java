package com.rentmanager.modules.review.infrastructure.persistence.mapper;

import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.infrastructure.persistence.entity.PlatformReviewJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class PlatformReviewPersistenceMapper {

    public PlatformReviewJpaEntity toJpaEntity(PlatformReview review) {
        if (review == null) {
            return null;
        }
        PlatformReviewJpaEntity jpa = new PlatformReviewJpaEntity();
        jpa.setId(review.getId());
        jpa.setVersion(review.getVersion());
        jpa.setReviewerUserId(review.getReviewerUserId());
        jpa.setReviewerName(review.getReviewerName());
        jpa.setRating(review.getRating());
        jpa.setComment(review.getComment());
        jpa.setStatus(review.getStatus());
        return jpa;
    }

    public PlatformReview toDomain(PlatformReviewJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return PlatformReview.rehydrate(
                jpa.getId(),
                jpa.getReviewerUserId(),
                jpa.getReviewerName(),
                jpa.getRating(),
                jpa.getComment(),
                jpa.getStatus(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}