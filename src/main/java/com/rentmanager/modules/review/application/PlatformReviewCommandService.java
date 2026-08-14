package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.dto.response.PlatformReviewResponse;
import com.rentmanager.modules.review.domain.enums.ReviewerType;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.domain.repository.PlatformReviewRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Submission and retrieval of platform reviews (V66). Open to every
 * authenticated user (landlord or renter): the identity and full name
 * come from the users table, never from the client.
 *
 * <p>One review per user: a first submission creates a {@code PENDING}
 * review; any later submission edits that same review ({@code
 * PlatformReview.replace}) and re-enters moderation — this is the
 * "editable, one per user" contract the UI builds on.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformReviewCommandService {

    private final PlatformReviewRepository platformReviewRepository;
    private final UserRepository userRepository;
    private final TenantProfileRepository tenantProfileRepository;

    @Transactional
    public PlatformReviewResponse submit(UUID reviewerUserId, int rating, String comment) {
        User user = userRepository.findById(reviewerUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found", ErrorCode.RESOURCE_NOT_FOUND));

        Optional<PlatformReview> existing = platformReviewRepository.findByReviewerUserId(reviewerUserId);
        PlatformReview review;
        if (existing.isPresent()) {
            review = existing.get();
            review.replace(rating, comment);
            platformReviewRepository.save(review);
            log.info("Platform review edited. reviewId={} userId={}", review.getId(), reviewerUserId);
        } else {
            review = PlatformReview.submit(
                    reviewerUserId, fullName(user), resolveReviewerType(user), rating, comment);
            review = platformReviewRepository.save(review);
            log.info("Platform review submitted. reviewId={} userId={} reviewerType={}",
                    review.getId(), reviewerUserId, review.getReviewerType());
        }
        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public PlatformReviewResponse getMyReview(UUID reviewerUserId) {
        return platformReviewRepository.findByReviewerUserId(reviewerUserId)
                .map(this::toResponse)
                .orElse(null);
    }

    private PlatformReviewResponse toResponse(PlatformReview review) {
        return new PlatformReviewResponse(
                ReviewModerationService.ReviewType.PLATFORM,
                review.getId(),
                review.getReviewerName(),
                review.getReviewerType(),
                review.getRating(),
                review.getComment(),
                review.getStatus(),
                review.getCreatedAt());
    }

    /**
     * Resolve the reviewer's side of the platform at write time (V69):
     * a user holding a tenant link is a landlord; a user with a
     * {@code tenant_profile} row (and no tenant link) is a renter.
     * Pending-onboarding users default to landlord, mirroring the
     * platform admin's {@code classifyUser} rule. Snapshotting keeps the
     * public testimonials wall truthful even if the account changes side
     * later.
     */
    private ReviewerType resolveReviewerType(User user) {
        if (user.getTenantId() != null) {
            return ReviewerType.LANDLORD;
        }
        if (user.getClerkUserId() != null
                && tenantProfileRepository.existsByClerkUserId(user.getClerkUserId())) {
            return ReviewerType.RENTER;
        }
        return ReviewerType.LANDLORD;
    }

    /**
     * Snapshot the reviewer's full name at write time. Users with a blank
     * profile name get {@code null}, which the domain turns into the
     * neutral {@code PlatformReview.DEFAULT_REVIEWER_NAME} — an email is
     * never stored as a review identity.
     */
    private static String fullName(User user) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? null : full;
    }
}