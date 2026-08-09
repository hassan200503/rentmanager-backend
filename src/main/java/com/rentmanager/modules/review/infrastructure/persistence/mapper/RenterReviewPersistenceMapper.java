package com.rentmanager.modules.review.infrastructure.persistence.mapper;

import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.infrastructure.persistence.entity.RenterReviewJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class RenterReviewPersistenceMapper {

    public RenterReviewJpaEntity toJpaEntity(RenterReview review) {
        if (review == null) {
            return null;
        }
        RenterReviewJpaEntity jpa = new RenterReviewJpaEntity();
        jpa.setId(review.getId());
        jpa.assignTenantIfUnset(review.getTenantId());
        jpa.setVersion(review.getVersion());
        jpa.setTenantProfileId(review.getTenantProfileId());
        jpa.setLeaseId(review.getLeaseId());
        jpa.setRating(review.getRating());
        jpa.setComment(review.getComment());
        jpa.setStatus(review.getStatus());
        return jpa;
    }

    public RenterReview toDomain(RenterReviewJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return RenterReview.rehydrate(
                jpa.getId(),
                jpa.getTenantId(),
                jpa.getTenantProfileId(),
                jpa.getLeaseId(),
                jpa.getRating(),
                jpa.getComment(),
                jpa.getStatus(),
                jpa.getVersion(),
                jpa.getCreatedAt(),
                jpa.getUpdatedAt()
        );
    }
}