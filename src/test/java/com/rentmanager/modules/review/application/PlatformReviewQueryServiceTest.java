package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.dto.response.PlatformReviewResponse;
import com.rentmanager.modules.review.application.dto.response.PlatformStatsResponse;
import com.rentmanager.modules.review.application.dto.response.PlatformTestimonialResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.enums.ReviewerType;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.model.PlatformReview;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.PlatformReviewRepository;
import com.rentmanager.modules.review.domain.repository.ReviewAggregationRepository;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V66 testimonials contract: the public feed is built exclusively from
 * APPROVED platform reviews (name snapshots redacted to first name);
 * landlord/renter reviews never leak into it. The moderation queue and
 * stats aggregate all three review kinds.
 */
class PlatformReviewQueryServiceTest {

    private ReviewAggregationRepository aggregationRepository;
    private PlatformReviewRepository platformReviewRepository;
    private TenantProfileRepository tenantProfileRepository;
    private TenantRepository tenantRepository;
    private PlatformReviewQueryService service;

    private final UUID platformReviewId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aggregationRepository = mock(ReviewAggregationRepository.class);
        platformReviewRepository = mock(PlatformReviewRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        tenantRepository = mock(TenantRepository.class);
        service = new PlatformReviewQueryService(
                aggregationRepository, platformReviewRepository,
                tenantProfileRepository, tenantRepository);
    }

    @Test
    void getTestimonials_returnsOnlyApprovedPlatformReviews() {
        PlatformReview approved = platform("Amina Hassan", ReviewStatus.APPROVED);
        when(platformReviewRepository.findByStatusOrderByCreatedAtDesc(
                ReviewStatus.APPROVED, 6)).thenReturn(List.of(approved));

        List<PlatformTestimonialResponse> testimonials = service.getTestimonials(6);

        assertEquals(1, testimonials.size());
        PlatformTestimonialResponse t = testimonials.get(0);
        assertEquals(platformReviewId, t.reviewId());
        assertEquals("Amina", t.reviewerFirstName());
        assertEquals(ReviewerType.LANDLORD, t.reviewerType());
        assertEquals(5, t.rating());
        verify(aggregationRepository, never())
                .findLandlordReviews(any(ReviewStatus.class), anyInt());
        verify(aggregationRepository, never())
                .findRenterReviews(any(ReviewStatus.class), anyInt());
    }

    @Test
    void getTestimonials_neverResolvesNamesFromProfilesOrTenants() {
        PlatformReview approved = platform("Brian Otieno", ReviewStatus.APPROVED);
        when(platformReviewRepository.findByStatusOrderByCreatedAtDesc(
                ReviewStatus.APPROVED, 6)).thenReturn(List.of(approved));

        service.getTestimonials(6);

        verify(tenantProfileRepository, never()).findById(any(UUID.class));
        verify(tenantRepository, never()).findById(any(UUID.class));
    }

    @Test
    void getTestimonials_sortsNewestFirst() {
        PlatformReview older = PlatformReview.rehydrate(UUID.randomUUID(),
                UUID.randomUUID(), "Grace Wanjiku", ReviewerType.RENTER, 4, "old", ReviewStatus.APPROVED,
                0L, Instant.parse("2026-01-01T00:00:00Z"), null);
        PlatformReview newer = PlatformReview.rehydrate(UUID.randomUUID(),
                UUID.randomUUID(), "Daniel Kipchoge", ReviewerType.RENTER, 5, "new", ReviewStatus.APPROVED,
                0L, Instant.parse("2026-02-01T00:00:00Z"), null);
        when(platformReviewRepository.findByStatusOrderByCreatedAtDesc(
                ReviewStatus.APPROVED, 10)).thenReturn(List.of(older, newer));

        List<PlatformTestimonialResponse> testimonials = service.getTestimonials(10);

        assertEquals("Daniel", testimonials.get(0).reviewerFirstName());
        assertEquals("Grace", testimonials.get(1).reviewerFirstName());
    }

    @Test
    void getTestimonials_emptyWhenNothingApproved() {
        when(platformReviewRepository.findByStatusOrderByCreatedAtDesc(
                ReviewStatus.APPROVED, 5)).thenReturn(List.of());

        assertTrue(service.getTestimonials(5).isEmpty());
    }

    @Test
    void listByStatus_includesAllThreeReviewKinds() {
        UUID landlordId = UUID.randomUUID();
        UUID renterId = UUID.randomUUID();
        LandlordReview landlord = LandlordReview.rehydrate(landlordId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 4, "landlord review",
                ReviewStatus.PENDING, 0L, Instant.parse("2026-01-01T00:00:00Z"), null);
        RenterReview renter = RenterReview.rehydrate(renterId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 3, "renter review",
                ReviewStatus.PENDING, 0L, Instant.parse("2026-01-02T00:00:00Z"), null);
        PlatformReview platformReview = platform("Amina Hassan", ReviewStatus.PENDING);

        when(aggregationRepository.findLandlordReviews(ReviewStatus.PENDING, 20))
                .thenReturn(List.of(landlord));
        when(aggregationRepository.findRenterReviews(ReviewStatus.PENDING, 20))
                .thenReturn(List.of(renter));
        when(aggregationRepository.findPlatformReviews(ReviewStatus.PENDING, 20))
                .thenReturn(List.of(platformReview));
        TenantProfile tenantProfile = TenantProfile.rehydrate(
                landlord.getTenantProfileId(), UUID.randomUUID(), "clerk_1",
                "Fatima Nyambura", "fatima@example.com", "0700000000", "12345678");
        Tenant tenant = mock(Tenant.class);
        when(tenant.getName()).thenReturn("Sunrise Properties");
        when(tenantProfileRepository.findById(landlord.getTenantProfileId()))
                .thenReturn(Optional.of(tenantProfile));
        when(tenantRepository.findById(renter.getTenantId()))
                .thenReturn(Optional.of(tenant));

        List<PlatformReviewResponse> reviews = service.listByStatus(ReviewStatus.PENDING, 20);

        assertEquals(3, reviews.size());
        assertEquals(ReviewModerationService.ReviewType.LANDLORD, reviews.get(2).type());
        assertEquals("Fatima Nyambura", reviews.get(2).reviewerName());
        assertNull(reviews.get(2).reviewerType());
        assertEquals(ReviewModerationService.ReviewType.RENTER, reviews.get(1).type());
        assertEquals("Sunrise Properties", reviews.get(1).reviewerName());
        assertNull(reviews.get(1).reviewerType());
        assertEquals(ReviewModerationService.ReviewType.PLATFORM, reviews.get(0).type());
        assertEquals("Amina Hassan", reviews.get(0).reviewerName());
        assertEquals(ReviewerType.LANDLORD, reviews.get(0).reviewerType());
    }

    @Test
    void getStats_includesPlatformCountsAndAverage() {
        when(aggregationRepository.countLandlordReviews(ReviewStatus.APPROVED)).thenReturn(1L);
        when(aggregationRepository.countLandlordReviews(ReviewStatus.PENDING)).thenReturn(0L);
        when(aggregationRepository.countLandlordReviews(ReviewStatus.HIDDEN)).thenReturn(0L);
        when(aggregationRepository.averageLandlordRating(ReviewStatus.APPROVED))
                .thenReturn(Optional.of(4.0));
        when(aggregationRepository.countRenterReviews(ReviewStatus.APPROVED)).thenReturn(2L);
        when(aggregationRepository.countRenterReviews(ReviewStatus.PENDING)).thenReturn(0L);
        when(aggregationRepository.countRenterReviews(ReviewStatus.HIDDEN)).thenReturn(0L);
        when(aggregationRepository.averageRenterRating(ReviewStatus.APPROVED))
                .thenReturn(Optional.of(3.0));
        when(aggregationRepository.countPlatformReviews(ReviewStatus.APPROVED)).thenReturn(5L);
        when(aggregationRepository.countPlatformReviews(ReviewStatus.PENDING)).thenReturn(1L);
        when(aggregationRepository.countPlatformReviews(ReviewStatus.HIDDEN)).thenReturn(1L);
        when(aggregationRepository.averagePlatformRating(ReviewStatus.APPROVED))
                .thenReturn(Optional.of(4.6));

        PlatformStatsResponse stats = service.getStats();

        assertEquals(5L, stats.platformApproved());
        assertEquals(1L, stats.platformPending());
        assertEquals(1L, stats.platformHidden());
        assertEquals(4.6, stats.platformAverageRating());
        assertEquals(1L, stats.landlordApproved());
        assertEquals(2L, stats.renterApproved());
    }

    @Test
    void getTestimonials_redactsBlankNameToNull() {
        PlatformReview approved = platform(null, ReviewStatus.APPROVED);
        when(platformReviewRepository.findByStatusOrderByCreatedAtDesc(
                ReviewStatus.APPROVED, 6)).thenReturn(List.of(approved));

        List<PlatformTestimonialResponse> testimonials = service.getTestimonials(6);

        assertNull(testimonials.get(0).reviewerFirstName());
    }

    @Test
    void getTestimonials_neverLeaksEmailLookalikeNames() {
        PlatformReview approved = platform("unknown@clerk.user", ReviewStatus.APPROVED);
        when(platformReviewRepository.findByStatusOrderByCreatedAtDesc(
                ReviewStatus.APPROVED, 6)).thenReturn(List.of(approved));

        List<PlatformTestimonialResponse> testimonials = service.getTestimonials(6);

        assertNull(testimonials.get(0).reviewerFirstName());
    }

    private PlatformReview platform(String reviewerName, ReviewStatus status) {
        return PlatformReview.rehydrate(platformReviewId, UUID.randomUUID(),
                reviewerName, ReviewerType.LANDLORD, 5, "testimonial", status, 0L,
                Instant.parse("2026-02-01T00:00:00Z"), null);
    }
}