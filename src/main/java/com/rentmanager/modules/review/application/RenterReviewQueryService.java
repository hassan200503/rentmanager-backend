package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.dto.response.RenterReviewResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewStatusCountsResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.RenterReviewRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side for landlord reviews of renters (V65, bidirectional ratings).
 *
 * <p>Same honesty and moderation rules as {@link ReviewQueryService}:
 * averages only below {@link ReviewQueryService#MIN_REVIEWS_TO_SHOW_AVERAGE}
 * approved reviews, and only {@code APPROVED} reviews ever reach public
 * surfaces. The renter's portal sees approved reviews as "ratings
 * received"; the landlord dashboard sees everything with status badges.</p>
 */
@Service
@RequiredArgsConstructor
public class RenterReviewQueryService {

    private final RenterReviewRepository reviewRepository;
    private final TenantProfileRepository tenantProfileRepository;

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(UUID landlordTenantId) {
        long count = reviewRepository.countApprovedByTenantId(landlordTenantId);
        if (count == 0) {
            return new ReviewSummaryResponse(0, null, false);
        }

        List<RenterReview> reviews = reviewRepository.findApprovedByTenantId(landlordTenantId);
        double average = reviews.stream()
                .mapToDouble(RenterReview::getRating)
                .average()
                .orElse(0.0);

        boolean shown = count >= ReviewQueryService.MIN_REVIEWS_TO_SHOW_AVERAGE;
        return new ReviewSummaryResponse(
                (int) count,
                shown ? Math.round(average * 10.0) / 10.0 : null,
                shown
        );
    }

    /**
     * The same summary, scoped to a single renter profile — what that
     * renter sees as "ratings received". The honest-average rule applies
     * to the renter's own approved reviews, never the landlord's
     * portfolio-wide total.
     */
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummaryForProfile(UUID landlordTenantId, UUID tenantProfileId) {
        if (tenantProfileId == null) {
            return new ReviewSummaryResponse(0, null, false);
        }
        List<RenterReview> reviews = reviewRepository.findApprovedByTenantId(landlordTenantId).stream()
                .filter(r -> tenantProfileId.equals(r.getTenantProfileId()))
                .toList();
        if (reviews.isEmpty()) {
            return new ReviewSummaryResponse(0, null, false);
        }

        double average = reviews.stream()
                .mapToDouble(RenterReview::getRating)
                .average()
                .orElse(0.0);

        boolean shown = reviews.size() >= ReviewQueryService.MIN_REVIEWS_TO_SHOW_AVERAGE;
        return new ReviewSummaryResponse(
                reviews.size(),
                shown ? Math.round(average * 10.0) / 10.0 : null,
                shown
        );
    }

    /**
     * Everything for the landlord dashboard, including pending and hidden
     * reviews so they can be badged in the UI. Never use on public routes.
     */
    @Transactional(readOnly = true)
    public List<RenterReviewResponse> getReviews(UUID landlordTenantId) {
        return reviewRepository.findByTenantId(landlordTenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Only {@code APPROVED} reviews — what a renter sees as "ratings
     * received" and what feeds public testimonials.
     */
    @Transactional(readOnly = true)
    public List<RenterReviewResponse> getApprovedReviews(UUID landlordTenantId, UUID tenantProfileId) {
        if (tenantProfileId == null) {
            return List.of();
        }
        return reviewRepository.findApprovedByTenantId(landlordTenantId).stream()
                .filter(r -> tenantProfileId.equals(r.getTenantProfileId()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RenterReviewResponse getReviewForProfile(UUID landlordTenantId, UUID tenantProfileId) {
        return reviewRepository.findByTenantIdAndTenantProfileId(landlordTenantId, tenantProfileId)
                .map(this::toResponse)
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

    private RenterReviewResponse toResponse(RenterReview review) {
        String renterName = null;
        if (review.getTenantProfileId() != null) {
            renterName = tenantProfileRepository.findById(review.getTenantProfileId())
                    .map(TenantProfile::getFullName)
                    .orElse(null);
        }
        return new RenterReviewResponse(
                review.getId(),
                renterName,
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt()
        );
    }
}