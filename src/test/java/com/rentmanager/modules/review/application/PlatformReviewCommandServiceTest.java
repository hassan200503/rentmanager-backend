package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.ReviewModerationService.ReviewType;
import com.rentmanager.modules.review.application.dto.response.PlatformReviewResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.enums.ReviewerType;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.domain.repository.PlatformReviewRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.model.User;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform review submission (V66): first submission creates a PENDING
 * review from the authenticated user's account; later submissions edit
 * the same review in place and re-enter moderation.
 */
class PlatformReviewCommandServiceTest {

    private PlatformReviewRepository platformReviewRepository;
    private UserRepository userRepository;
    private TenantProfileRepository tenantProfileRepository;
    private PlatformReviewCommandService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        platformReviewRepository = mock(PlatformReviewRepository.class);
        userRepository = mock(UserRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        service = new PlatformReviewCommandService(
                platformReviewRepository, userRepository, tenantProfileRepository);
    }

    @Test
    void submit_firstTime_createsPendingReviewWithSnapshotName() {
        User user = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(platformReviewRepository.findByReviewerUserId(userId)).thenReturn(Optional.empty());
        when(platformReviewRepository.save(any(PlatformReview.class)))
                .thenAnswer(inv -> {
                    PlatformReview review = inv.getArgument(0);
                    review.setId(UUID.randomUUID());
                    return review;
                });

        PlatformReviewResponse response = service.submit(userId, 5, "Amazing");

        assertEquals(ReviewType.PLATFORM, response.type());
        assertEquals("Amina Hassan", response.reviewerName());
        assertEquals(ReviewerType.LANDLORD, response.reviewerType());
        assertEquals(ReviewStatus.PENDING, response.status());
        verify(platformReviewRepository).save(any(PlatformReview.class));
    }

    @Test
    void submit_landlordWithTenantLink_isSnapshottedAsLandlord() {
        User user = user();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(platformReviewRepository.findByReviewerUserId(userId)).thenReturn(Optional.empty());
        when(platformReviewRepository.save(any(PlatformReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlatformReviewResponse response = service.submit(userId, 5, "Amazing");

        assertEquals(ReviewerType.LANDLORD, response.reviewerType());
    }

    @Test
    void submit_renterWithoutTenantLink_isSnapshottedAsRenter() {
        User user = User.createFromClerk("clerk_1", "user@example.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(tenantProfileRepository.existsByClerkUserId("clerk_1")).thenReturn(true);
        when(platformReviewRepository.findByReviewerUserId(userId)).thenReturn(Optional.empty());
        when(platformReviewRepository.save(any(PlatformReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlatformReviewResponse response = service.submit(userId, 4, "Renter opinion");

        assertEquals(ReviewerType.RENTER, response.reviewerType());
    }

    @Test
    void submit_secondTime_editsExistingReview() {
        User user = user();
        PlatformReview existing = PlatformReview.submit(
                userId, "Amina Hassan", ReviewerType.LANDLORD, 5, "old");
        existing.approve();
        existing.setId(UUID.randomUUID());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(platformReviewRepository.findByReviewerUserId(userId))
                .thenReturn(Optional.of(existing));
        when(platformReviewRepository.save(any(PlatformReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlatformReviewResponse response = service.submit(userId, 2, "edited");

        assertEquals(2, response.rating());
        assertEquals("edited", response.comment());
        assertEquals(ReviewStatus.PENDING, response.status());
        assertEquals(existing.getId(), response.reviewId());
    }

    @Test
    void submit_unknownUser_throws() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.submit(userId, 5, "ok"));
        verify(platformReviewRepository, never()).save(any());
    }

@Test
    void submit_blankNames_useNeutralDisplayNameNeverEmail() {
        User user = User.createInvited("clerk_1", "user@example.com", "  ", "",
                UUID.randomUUID(), UserRole.OWNER);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(platformReviewRepository.findByReviewerUserId(userId)).thenReturn(Optional.empty());
        when(platformReviewRepository.save(any(PlatformReview.class)))
                .thenAnswer(inv -> {
                    PlatformReview review = inv.getArgument(0);
                    review.setId(UUID.randomUUID());
                    return review;
                });

        PlatformReviewResponse response = service.submit(userId, 4, "ok");

        assertEquals(PlatformReview.DEFAULT_REVIEWER_NAME, response.reviewerName());
        assertFalse(response.reviewerName().contains("@"));
    }

    @Test
    void getMyReview_returnsNullWhenNoneSubmitted() {
        when(platformReviewRepository.findByReviewerUserId(userId)).thenReturn(Optional.empty());

        assertNull(service.getMyReview(userId));
    }

    @Test
    void getMyReview_returnsExistingReview() {
        PlatformReview existing = PlatformReview.submit(
                userId, "Amina Hassan", ReviewerType.LANDLORD, 4, "nice");
        existing.setId(UUID.randomUUID());
        when(platformReviewRepository.findByReviewerUserId(userId))
                .thenReturn(Optional.of(existing));

        PlatformReviewResponse response = service.getMyReview(userId);

        assertEquals(existing.getId(), response.reviewId());
        assertEquals("Amina Hassan", response.reviewerName());
    }

    private User user() {
        return User.createInvited("clerk_1", "user@example.com",
                "Amina", "Hassan", UUID.randomUUID(), UserRole.OWNER);
    }
}