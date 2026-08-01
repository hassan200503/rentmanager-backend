package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.repository.LandlordReviewRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side for landlord reviews (Phase 4b).
 *
 * <p>Honesty rule: an average rating is only ever exposed once at least
 * {@link #MIN_REVIEWS_TO_SHOW_AVERAGE} reviews exist. Below that the
 * summary reports the count only and {@code averageRating} stays null -
 * a trust signal built on a single review is misleading.</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    public static final int MIN_REVIEWS_TO_SHOW_AVERAGE = 3;

    private final LandlordReviewRepository reviewRepository;
    private final TenantProfileRepository tenantProfileRepository;

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(UUID landlordTenantId) {
        long count = reviewRepository.countByTenantId(landlordTenantId);
        if (count == 0) {
            return new ReviewSummaryResponse(0, null, false);
        }

        List<LandlordReview> reviews = reviewRepository.findByTenantId(landlordTenantId);
        double average = reviews.stream()
                .mapToDouble(LandlordReview::getRating)
                .average()
                .orElse(0.0);

        boolean shown = count >= MIN_REVIEWS_TO_SHOW_AVERAGE;
        return new ReviewSummaryResponse(
                (int) count,
                shown ? Math.round(average * 10.0) / 10.0 : null,
                shown
        );
    }

    @Transactional(readOnly = true)
    public List<LandlordReviewResponse> getReviews(UUID landlordTenantId) {
        return reviewRepository.findByTenantId(landlordTenantId).stream()
                .map(review -> toResponse(landlordTenantId, review))
                .toList();
    }

    @Transactional(readOnly = true)
    public LandlordReviewResponse getRenterReview(UUID landlordTenantId, UUID tenantProfileId) {
        return reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId)
                .map(review -> toResponse(landlordTenantId, review))
                .orElse(null);
    }

    private LandlordReviewResponse toResponse(UUID landlordTenantId, LandlordReview review) {
        String renterName = null;
        if (review.getTenantProfileId() != null) {
            renterName = tenantProfileRepository.findById(review.getTenantProfileId())
                    .map(TenantProfile::getFullName)
                    .orElse(null);
        }
        return new LandlordReviewResponse(
                review.getId(),
                renterName,
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
