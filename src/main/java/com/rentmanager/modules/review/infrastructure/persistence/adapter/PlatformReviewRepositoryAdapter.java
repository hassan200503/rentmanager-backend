package com.rentmanager.modules.review.infrastructure.persistence.adapter;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.domain.repository.PlatformReviewRepository;
import com.rentmanager.modules.review.infrastructure.persistence.entity.PlatformReviewJpaEntity;
import com.rentmanager.modules.review.infrastructure.persistence.mapper.PlatformReviewPersistenceMapper;
import com.rentmanager.modules.review.infrastructure.persistence.repository.PlatformReviewJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PlatformReviewRepositoryAdapter implements PlatformReviewRepository {

    private final PlatformReviewJpaRepository jpaRepository;
    private final PlatformReviewPersistenceMapper mapper;

    @Override
    public PlatformReview save(PlatformReview review) {
        PlatformReviewJpaEntity saved = jpaRepository.save(mapper.toJpaEntity(review));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PlatformReview> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PlatformReview> findByReviewerUserId(UUID reviewerUserId) {
        return jpaRepository.findByReviewerUserId(reviewerUserId).map(mapper::toDomain);
    }

    @Override
    public List<PlatformReview> findByStatusOrderByCreatedAtDesc(ReviewStatus status, int limit) {
        return jpaRepository
                .findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, Math.max(limit, 1)))
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public long countByStatus(ReviewStatus status) {
        return jpaRepository.countByStatus(status);
    }

    @Override
    public Optional<Double> averageRatingByStatus(ReviewStatus status) {
        return jpaRepository.findAverageRatingByStatus(status);
    }
}