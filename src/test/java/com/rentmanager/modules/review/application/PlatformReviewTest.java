package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.enums.ReviewerType;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Domain invariants of a platform review (V66/V69): tenant-less lifecycle,
 * strict validation, one-review-per-user in-place editing via replace()
 * that always re-enters moderation, approve/hide transitions, and the
 * reviewer's side (landlord/renter) snapshotted at submit time.
 */
class PlatformReviewTest {

    private final UUID reviewerId = UUID.randomUUID();

    @Test
    void submit_createsPendingReviewWithSnapshot() {
        PlatformReview review = PlatformReview.submit(
                reviewerId, "Amina Hassan", ReviewerType.RENTER, 5, "  Life-changing  ");

        assertEquals("Amina Hassan", review.getReviewerName());
        assertEquals(ReviewerType.RENTER, review.getReviewerType());
        assertEquals(5, review.getRating());
        assertEquals("Life-changing", review.getComment());
        assertEquals(ReviewStatus.PENDING, review.getStatus());
        assertNull(review.getId());
    }

    @Test
    void submit_rejectsNullReviewer() {
        assertThrows(IllegalArgumentException.class,
                () -> PlatformReview.submit(null, "Amina", ReviewerType.RENTER, 5, "ok"));
    }

    @Test
    void submit_rejectsNullReviewerType() {
        assertThrows(IllegalArgumentException.class,
                () -> PlatformReview.submit(reviewerId, "Amina", null, 5, "ok"));
    }

    @Test
    void submit_blankName_fallsBackToNeutralDisplayName() {
        PlatformReview review = PlatformReview.submit(reviewerId, " ", ReviewerType.LANDLORD, 5, "ok");

        assertEquals(PlatformReview.DEFAULT_REVIEWER_NAME, review.getReviewerName());
    }

    @Test
    void submit_rejectsRatingOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 0, "ok"));
        assertThrows(IllegalArgumentException.class,
                () -> PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 6, "ok"));
    }

    @Test
    void submit_rejectsOversizedComment() {
        assertThrows(IllegalArgumentException.class,
                () -> PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, "x".repeat(1001)));
    }

    @Test
    void submit_allowsBlankComment() {
        PlatformReview review = PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, " ");

        assertNull(review.getComment());
    }

    @Test
    void replace_editsInPlaceAndReEntersModeration() {
        PlatformReview review = PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, "great");
        review.approve();

        review.replace(2, "changed my mind");

        assertEquals(2, review.getRating());
        assertEquals(ReviewStatus.PENDING, review.getStatus());
        assertEquals(ReviewerType.LANDLORD, review.getReviewerType());
    }

    @Test
    void replace_rejectsInvalidRatingAndOversizedComment() {
        PlatformReview review = PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, "ok");

        assertThrows(IllegalArgumentException.class, () -> review.replace(0, "x"));
        assertThrows(IllegalArgumentException.class, () -> review.replace(5, "x".repeat(1001)));
    }

    @Test
    void replace_hiddenReviewReEntersModeration() {
        PlatformReview review = PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, "ok");
        review.hide();

        review.replace(4, "rewritten");

        assertEquals(4, review.getRating());
        assertEquals(ReviewStatus.PENDING, review.getStatus());
    }

    @Test
    void approve_publishesPendingAndHidReview() {
        PlatformReview pending = PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, "ok");
        pending.approve();
        assertEquals(ReviewStatus.APPROVED, pending.getStatus());

        PlatformReview hidden = PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, "ok");
        hidden.hide();
        hidden.approve();
        assertEquals(ReviewStatus.APPROVED, hidden.getStatus());
    }

    @Test
    void approve_alreadyApproved_throws() {
        PlatformReview review = PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, "ok");
        review.approve();

        assertThrows(IllegalStateException.class, review::approve);
    }

    @Test
    void hide_removesFromFeedAndStaysHidden() {
        PlatformReview review = PlatformReview.submit(reviewerId, "Amina", ReviewerType.LANDLORD, 5, "ok");
        review.hide();

        assertEquals(ReviewStatus.HIDDEN, review.getStatus());
        assertThrows(IllegalStateException.class, review::hide);
    }

    @Test
    void rehydrate_restoresFullState() {
        UUID id = UUID.randomUUID();
        java.time.Instant now = java.time.Instant.now();
        PlatformReview review = PlatformReview.rehydrate(
                id, reviewerId, "Amina Hassan", ReviewerType.RENTER, 4, "good", ReviewStatus.HIDDEN,
                7L, now, now);

        assertEquals(id, review.getId());
        assertEquals(reviewerId, review.getReviewerUserId());
        assertEquals(ReviewerType.RENTER, review.getReviewerType());
        assertEquals(ReviewStatus.HIDDEN, review.getStatus());
        assertEquals(7L, review.getVersion());
        assertEquals(now, review.getCreatedAt());
    }
}
