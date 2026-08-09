package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewStatusCountsResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
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
 * Read side for landlord reviews (Phase 4b, moderated since V65).
 *
 * <p>Honesty rule: an average rating is only ever exposed once at least
 * {@link #MIN_REVIEWS_TO_SHOW_AVERAGE} reviews exist. Below that the
 * summary reports the count only and {@code averageRating} stays null -
 * a trust signal built on a single review is misleading.</p>
 *
 * <p>Moderation rule (V65): only {@code APPROVED} reviews feed the
 * summary and the {@link #getApprovedReviews(UUID)} list - public
 * surfaces must never see pending or hidden content. The dashboard's
 * {@link #getReviews(UUID)} still returns everything with its status so
 * the landlord UI can badge pending approvals.</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewQueryService {

    public static final int MIN_REVIEWS_TO_SHOW_AVERAGE = 3;

    private final LandlordReviewRepository reviewRepository;
    private final TenantProfileRepository tenantProfileRepository;

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(UUID landlordTenantId) {
        long count = reviewRepository.countApprovedByTenantId(landlordTenantId);
        if (count == 0) {
            return new ReviewSummaryResponse(0, null, false);
        }

        List<LandlordReview> reviews = reviewRepository.findApprovedByTenantId(landlordTenantId);
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

    /**
     * Everything for the landlord dashboard, including pending and hidden
     * reviews so they can be badged in the UI. Safest default for a
     * tenant-scoped read; never use this on public routes.
     */
    @Transactional(readOnly = true)
    public List<LandlordReviewResponse> getReviews(UUID landlordTenantId) {
        return reviewRepository.findByTenantId(landlordTenantId).stream()
                .map(review -> toResponse(landlordTenantId, review))
                .toList();
    }

    /**
     * Only {@code APPROVED} reviews — the public listing pages both call
     * this, never {@link #getReviews(UUID)}.
     */
    @Transactional(readOnly = true)
    public List<LandlordReviewResponse> getApprovedReviews(UUID landlordTenantId) {
        return reviewRepository.findApprovedByTenantId(landlordTenantId).stream()
                .map(review -> toResponse(landlordTenantId, review))
                .toList();
    }

    @Transactional(readOnly = true)
    public LandlordReviewResponse getRenterReview(UUID landlordTenantId, UUID tenantProfileId) {
        return reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId)
                .map(review -> toResponse(landlordTenantId, review))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ReviewStatusCountsResponse getStatusCounts(UUID landlordTenantId) {
        long approved = reviewRepository.countApprovedByTenantId(landlordTenantId);
        long pending = reviewRepository
                .countByTenantIdAndStatus(landlordTenantId, ReviewStatus.PENDING);
        long hidden = reviewRepository
                .countByTenantIdAndStatus(landlordTenantId, ReviewStatus.HIDDEN);
        return new ReviewStatusCountsResponse(approved, pending, hidden);
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
                review.getStatus(),
                review.getCreatedAt()
        );
    }
}