package com.rentmanager.modules.review.infrastructure.persistence.adapter;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.ReviewAggregationRepository;
import com.rentmanager.modules.review.infrastructure.persistence.mapper.LandlordReviewPersistenceMapper;
import com.rentmanager.modules.review.infrastructure.persistence.mapper.PlatformReviewPersistenceMapper;
import com.rentmanager.modules.review.infrastructure.persistence.mapper.RenterReviewPersistenceMapper;
import com.rentmanager.modules.review.infrastructure.persistence.repository.LandlordReviewJpaRepository;
import com.rentmanager.modules.review.infrastructure.persistence.repository.PlatformReviewJpaRepository;
import com.rentmanager.modules.review.infrastructure.persistence.repository.RenterReviewJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ReviewAggregationRepositoryAdapter implements ReviewAggregationRepository {

    private final LandlordReviewJpaRepository landlordReviewJpaRepository;
    private final RenterReviewJpaRepository renterReviewJpaRepository;
    private final PlatformReviewJpaRepository platformReviewJpaRepository;
    private final LandlordReviewPersistenceMapper landlordMapper;
    private final RenterReviewPersistenceMapper renterMapper;
    private final PlatformReviewPersistenceMapper platformMapper;

    @Override
    public List<LandlordReview> findLandlordReviews(ReviewStatus status, int limit) {
        return landlordReviewJpaRepository
                .findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, Math.max(limit, 1)))
                .stream().map(landlordMapper::toDomain).toList();
    }

    @Override
    public List<RenterReview> findRenterReviews(ReviewStatus status, int limit) {
        return renterReviewJpaRepository
                .findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, Math.max(limit, 1)))
                .stream().map(renterMapper::toDomain).toList();
    }

    @Override
    public List<PlatformReview> findPlatformReviews(ReviewStatus status, int limit) {
        return platformReviewJpaRepository
                .findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, Math.max(limit, 1)))
                .stream().map(platformMapper::toDomain).toList();
    }

    @Override
    public long countLandlordReviews(ReviewStatus status) {
        return landlordReviewJpaRepository.countByStatus(status);
    }

    @Override
    public long countRenterReviews(ReviewStatus status) {
        return renterReviewJpaRepository.countByStatus(status);
    }

    @Override
    public long countPlatformReviews(ReviewStatus status) {
        return platformReviewJpaRepository.countByStatus(status);
    }

    @Override
    public Optional<Double> averageLandlordRating(ReviewStatus status) {
        return landlordReviewJpaRepository.findAverageRatingByStatus(status);
    }

    @Override
    public Optional<Double> averageRenterRating(ReviewStatus status) {
        return renterReviewJpaRepository.findAverageRatingByStatus(status);
    }

    @Override
    public Optional<Double> averagePlatformRating(ReviewStatus status) {
        return platformReviewJpaRepository.findAverageRatingByStatus(status);
    }
}