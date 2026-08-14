package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.ReviewModerationService.ReviewType;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.enums.ReviewerType;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.LandlordReviewRepository;
import com.rentmanager.modules.review.domain.repository.PlatformReviewRepository;
import com.rentmanager.modules.review.domain.repository.RenterReviewRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V65/V66 moderation workflow: PENDING -> APPROVED publishes; APPROVED ->
 * HIDDEN removes from public surfaces; re-approval of a hidden review is
 * an explicit new decision. Works identically for all three review kinds.
 */
class ReviewModerationServiceTest {

    private LandlordReviewRepository landlordReviewRepository;
    private RenterReviewRepository renterReviewRepository;
    private PlatformReviewRepository platformReviewRepository;
    private ReviewModerationService service;

    private final UUID reviewId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        landlordReviewRepository = mock(LandlordReviewRepository.class);
        renterReviewRepository = mock(RenterReviewRepository.class);
        platformReviewRepository = mock(PlatformReviewRepository.class);
        service = new ReviewModerationService(
                landlordReviewRepository, renterReviewRepository, platformReviewRepository);
    }

    @Test
    void approve_pendingLandlordReviewPublishesIt() {
        LandlordReview pending = landlord(ReviewStatus.PENDING);
        when(landlordReviewRepository.findById(reviewId)).thenReturn(Optional.of(pending));
        when(landlordReviewRepository.save(any(LandlordReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve(ReviewType.LANDLORD, reviewId);

        assertEquals(ReviewStatus.APPROVED, pending.getStatus());
        verify(landlordReviewRepository).save(pending);
    }

    @Test
    void approve_pendingRenterReviewPublishesIt() {
        RenterReview pending = renter(ReviewStatus.PENDING);
        when(renterReviewRepository.findById(reviewId)).thenReturn(Optional.of(pending));
        when(renterReviewRepository.save(any(RenterReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve(ReviewType.RENTER, reviewId);

        assertEquals(ReviewStatus.APPROVED, pending.getStatus());
        verify(renterReviewRepository).save(pending);
    }

    @Test
    void approve_pendingPlatformReviewPublishesIt() {
        PlatformReview pending = platform(ReviewStatus.PENDING);
        when(platformReviewRepository.findById(reviewId)).thenReturn(Optional.of(pending));
        when(platformReviewRepository.save(any(PlatformReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve(ReviewType.PLATFORM, reviewId);

        assertEquals(ReviewStatus.APPROVED, pending.getStatus());
        verify(platformReviewRepository).save(pending);
    }

    @Test
    void hide_platformReviewRemovesFromPublicFeed() {
        PlatformReview approved = platform(ReviewStatus.APPROVED);
        when(platformReviewRepository.findById(reviewId)).thenReturn(Optional.of(approved));
        when(platformReviewRepository.save(any(PlatformReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.hide(ReviewType.PLATFORM, reviewId);

        assertEquals(ReviewStatus.HIDDEN, approved.getStatus());
    }

    @Test
    void approve_unknownPlatformReview_throws() {
        when(platformReviewRepository.findById(reviewId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.approve(ReviewType.PLATFORM, reviewId));
        verify(platformReviewRepository, never()).save(any());
    }

    @Test
    void approve_hiddenReviewRepublishesIt() {
        LandlordReview hidden = landlord(ReviewStatus.HIDDEN);
        when(landlordReviewRepository.findById(reviewId)).thenReturn(Optional.of(hidden));
        when(landlordReviewRepository.save(any(LandlordReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.approve(ReviewType.LANDLORD, reviewId);

        assertEquals(ReviewStatus.APPROVED, hidden.getStatus());
    }

    @Test
    void approve_alreadyApprovedReview_throws() {
        LandlordReview approved = landlord(ReviewStatus.APPROVED);
        when(landlordReviewRepository.findById(reviewId)).thenReturn(Optional.of(approved));

        assertThrows(IllegalStateException.class,
                () -> service.approve(ReviewType.LANDLORD, reviewId));
        verify(landlordReviewRepository, never()).save(any());
    }

    @Test
    void hide_approvedReviewRemovesFromPublicSurfaces() {
        LandlordReview approved = landlord(ReviewStatus.APPROVED);
        when(landlordReviewRepository.findById(reviewId)).thenReturn(Optional.of(approved));
        when(landlordReviewRepository.save(any(LandlordReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.hide(ReviewType.LANDLORD, reviewId);

        assertEquals(ReviewStatus.HIDDEN, approved.getStatus());
    }

    @Test
    void hide_alreadyHiddenReview_throws() {
        LandlordReview hidden = landlord(ReviewStatus.HIDDEN);
        when(landlordReviewRepository.findById(reviewId)).thenReturn(Optional.of(hidden));

        assertThrows(IllegalStateException.class,
                () -> service.hide(ReviewType.LANDLORD, reviewId));
        verify(landlordReviewRepository, never()).save(any());
    }

    @Test
    void unknownReview_throwsWithoutSaving() {
        when(landlordReviewRepository.findById(reviewId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.approve(ReviewType.LANDLORD, reviewId));
        verify(landlordReviewRepository, never()).save(any());
    }

    private LandlordReview landlord(ReviewStatus status) {
        return LandlordReview.rehydrate(reviewId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 5, "comment", status, 0L, null, null);
    }

    private RenterReview renter(ReviewStatus status) {
        return RenterReview.rehydrate(reviewId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 5, "comment", status, 0L, null, null);
    }

    private PlatformReview platform(ReviewStatus status) {
        return PlatformReview.rehydrate(reviewId, UUID.randomUUID(), "Amina Hassan",
                ReviewerType.LANDLORD, 5, "comment", status, 0L, null, null);
    }
}