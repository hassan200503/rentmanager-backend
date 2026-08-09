package com.rentmanager.modules.review.infrastructure.persistence.mapper;

import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.infrastructure.persistence.entity.LandlordReviewJpaEntity;
import org.springframework.stereotype.Component;

@Component
public class LandlordReviewPersistenceMapper {

    public LandlordReviewJpaEntity toJpaEntity(LandlordReview review) {
        if (review == null) {
            return null;
        }
        LandlordReviewJpaEntity jpa = new LandlordReviewJpaEntity();
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

    public LandlordReview toDomain(LandlordReviewJpaEntity jpa) {
        if (jpa == null) {
            return null;
        }
        return LandlordReview.rehydrate(
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