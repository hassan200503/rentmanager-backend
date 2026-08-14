package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.dto.response.PlatformReviewResponse;
import com.rentmanager.modules.review.application.dto.response.PlatformStatsResponse;
import com.rentmanager.modules.review.application.dto.response.PlatformTestimonialResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.repository.PlatformReviewRepository;
import com.rentmanager.modules.review.domain.repository.ReviewAggregationRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Platform-wide review reads (V65/V66): the moderation queue, moderation
 * stats, and the public testimonials feed. Every query here crosses
 * tenant boundaries on purpose and is only wired into the platform admin
 * surface and the public testimonials endpoint.
 *
 * <p>Testimonials (V66): the public feed is exclusively built from
 * approved <em>platform</em> reviews — reviews OF RentManager, not of a
 * landlord service. The reviewer's full name is snapshotted at write
 * time and redacted to the first name on this surface.</p>
 */
@Service
@RequiredArgsConstructor
public class PlatformReviewQueryService {

    private final ReviewAggregationRepository reviewAggregationRepository;
    private final PlatformReviewRepository platformReviewRepository;
    private final TenantProfileRepository tenantProfileRepository;
    private final TenantRepository tenantRepository;

    @Transactional(readOnly = true)
    public List<PlatformReviewResponse> listByStatus(ReviewStatus status, int limit) {
        List<PlatformReviewResponse> reviews = new ArrayList<>();
        reviewAggregationRepository.findLandlordReviews(status, limit).forEach(r ->
                reviews.add(new PlatformReviewResponse(
                        ReviewModerationService.ReviewType.LANDLORD,
                        r.getId(),
                        resolveRenterName(r.getTenantProfileId()),
                        null,
                        r.getRating(),
                        r.getComment(),
                        r.getStatus(),
                        r.getCreatedAt())));
        reviewAggregationRepository.findRenterReviews(status, limit).forEach(r ->
                reviews.add(new PlatformReviewResponse(
                        ReviewModerationService.ReviewType.RENTER,
                        r.getId(),
                        resolveLandlordName(r.getTenantId()),
                        null,
                        r.getRating(),
                        r.getComment(),
                        r.getStatus(),
                        r.getCreatedAt())));
        reviewAggregationRepository.findPlatformReviews(status, limit).forEach(r ->
                reviews.add(new PlatformReviewResponse(
                        ReviewModerationService.ReviewType.PLATFORM,
                        r.getId(),
                        r.getReviewerName(),
                        r.getReviewerType(),
                        r.getRating(),
                        r.getComment(),
                        r.getStatus(),
                        r.getCreatedAt())));
        reviews.sort(Comparator.comparing(PlatformReviewResponse::createdAt).reversed());
        return reviews;
    }

    @Transactional(readOnly = true)
    public List<PlatformTestimonialResponse> getTestimonials(int limit) {
        return platformReviewRepository
                .findByStatusOrderByCreatedAtDesc(ReviewStatus.APPROVED, Math.max(limit, 1))
                .stream()
                .map(r -> new PlatformTestimonialResponse(
                        r.getId(),
                        toFirstName(r.getReviewerName()),
                        r.getReviewerType(),
                        r.getRating(),
                        r.getComment(),
                        r.getCreatedAt()))
                .sorted(Comparator.comparing(
                        PlatformTestimonialResponse::createdAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public PlatformStatsResponse getStats() {
        return new PlatformStatsResponse(
                reviewAggregationRepository.countLandlordReviews(ReviewStatus.APPROVED),
                reviewAggregationRepository.countLandlordReviews(ReviewStatus.PENDING),
                reviewAggregationRepository.countLandlordReviews(ReviewStatus.HIDDEN),
                reviewAggregationRepository.averageLandlordRating(ReviewStatus.APPROVED).orElse(0.0),
                reviewAggregationRepository.countRenterReviews(ReviewStatus.APPROVED),
                reviewAggregationRepository.countRenterReviews(ReviewStatus.PENDING),
                reviewAggregationRepository.countRenterReviews(ReviewStatus.HIDDEN),
                reviewAggregationRepository.averageRenterRating(ReviewStatus.APPROVED).orElse(0.0),
                reviewAggregationRepository.countPlatformReviews(ReviewStatus.APPROVED),
                reviewAggregationRepository.countPlatformReviews(ReviewStatus.PENDING),
                reviewAggregationRepository.countPlatformReviews(ReviewStatus.HIDDEN),
                reviewAggregationRepository.averagePlatformRating(ReviewStatus.APPROVED).orElse(0.0)
        );
    }

    private String resolveRenterName(UUID tenantProfileId) {
        if (tenantProfileId == null) {
            return null;
        }
        return tenantProfileRepository.findById(tenantProfileId)
                .map(TenantProfile::getFullName)
                .orElse(null);
    }

    private String resolveLandlordName(UUID tenantId) {
        if (tenantId == null) {
            return null;
        }
        return tenantRepository.findById(tenantId)
                .map(Tenant::getName)
                .orElse(null);
    }

    private static String toFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String trimmed = fullName.trim();
        if (trimmed.contains("@")) {
            return null;
        }
        return trimmed.split("\\s+")[0];
    }
}