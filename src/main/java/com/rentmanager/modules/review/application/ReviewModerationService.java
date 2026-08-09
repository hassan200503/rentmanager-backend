package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.LandlordReviewRepository;
import com.rentmanager.modules.review.domain.repository.PlatformReviewRepository;
import com.rentmanager.modules.review.domain.repository.RenterReviewRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Platform moderation of reviews (V65), used only by the platform admin
 * surface. Both directions of a tenancy flow through the same domain
 * transitions: {@code PENDING} -> {@code APPROVED} publishes a review,
 * and {@code APPROVED}/{@code PENDING} -> {@code HIDDEN} removes it from
 * every public surface while keeping the audit trail intact. Re-approving
 * a hidden review is an explicit new moderation decision.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewModerationService {

    public enum ReviewType {
        LANDLORD,
        RENTER,
        PLATFORM
    }

    private final LandlordReviewRepository landlordReviewRepository;
    private final RenterReviewRepository renterReviewRepository;
    private final PlatformReviewRepository platformReviewRepository;

    @Transactional
    public void approve(ReviewType type, UUID reviewId) {
        switch (type) {
            case LANDLORD -> {
                LandlordReview review = findLandlord(reviewId);
                review.approve();
                landlordReviewRepository.save(review);
            }
            case RENTER -> {
                RenterReview review = findRenter(reviewId);
                review.approve();
                renterReviewRepository.save(review);
            }
            case PLATFORM -> {
                PlatformReview review = findPlatform(reviewId);
                review.approve();
                platformReviewRepository.save(review);
            }
        }
        log.info("Review approved. type={} reviewId={}", type, reviewId);
    }

    @Transactional
    public void hide(ReviewType type, UUID reviewId) {
        switch (type) {
            case LANDLORD -> {
                LandlordReview review = findLandlord(reviewId);
                review.hide();
                landlordReviewRepository.save(review);
            }
            case RENTER -> {
                RenterReview review = findRenter(reviewId);
                review.hide();
                renterReviewRepository.save(review);
            }
            case PLATFORM -> {
                PlatformReview review = findPlatform(reviewId);
                review.hide();
                platformReviewRepository.save(review);
            }
        }
        log.info("Review hidden. type={} reviewId={}", type, reviewId);
    }

    private LandlordReview findLandlord(UUID reviewId) {
        return landlordReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Landlord review not found", ErrorCode.RESOURCE_NOT_FOUND));
    }

    private RenterReview findRenter(UUID reviewId) {
        return renterReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Renter review not found", ErrorCode.RESOURCE_NOT_FOUND));
    }

    private PlatformReview findPlatform(UUID reviewId) {
        return platformReviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Platform review not found", ErrorCode.RESOURCE_NOT_FOUND));
    }
}