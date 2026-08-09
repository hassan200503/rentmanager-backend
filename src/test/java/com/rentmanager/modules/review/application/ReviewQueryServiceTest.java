package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.LandlordReview;
import com.rentmanager.modules.review.domain.repository.LandlordReviewRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 4b + V65: an average rating is only ever exposed once at least 3
 * approved reviews exist — a trust signal built on one or two reviews is
 * misleading, and pending/hidden content never counts.
 */
class ReviewQueryServiceTest {

    private LandlordReviewRepository reviewRepository;
    private TenantProfileRepository tenantProfileRepository;
    private ReviewQueryService service;

    private final UUID landlordTenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reviewRepository = mock(LandlordReviewRepository.class);
        tenantProfileRepository = mock(TenantProfileRepository.class);
        service = new ReviewQueryService(reviewRepository, tenantProfileRepository);
    }

    @Test
    void summaryIsEmpty_whenNoReviews() {
        when(reviewRepository.countApprovedByTenantId(landlordTenantId)).thenReturn(0L);

        ReviewSummaryResponse summary = service.getSummary(landlordTenantId);

        assertEquals(0, summary.reviewCount());
        assertNull(summary.averageRating());
        assertFalse(summary.averageShown());
    }

    @Test
    void averageHidden_belowThreeReviews() {
        List<LandlordReview> twoReviews = List.of(
                review(5), review(1));
        when(reviewRepository.countApprovedByTenantId(landlordTenantId)).thenReturn(2L);
        when(reviewRepository.findApprovedByTenantId(landlordTenantId)).thenReturn(twoReviews);

        ReviewSummaryResponse summary = service.getSummary(landlordTenantId);

        assertEquals(2, summary.reviewCount());
        assertNull(summary.averageRating());
        assertFalse(summary.averageShown());
    }

    @Test
    void averageShown_atThreeReviews() {
        List<LandlordReview> threeReviews = List.of(review(5), review(5), review(4));
        when(reviewRepository.countApprovedByTenantId(landlordTenantId)).thenReturn(3L);
        when(reviewRepository.findApprovedByTenantId(landlordTenantId)).thenReturn(threeReviews);

        ReviewSummaryResponse summary = service.getSummary(landlordTenantId);

        assertEquals(3, summary.reviewCount());
        assertEquals(4.7, summary.averageRating());
        assertTrue(summary.averageShown());
    }

    @Test
    void averageRoundedToOneDecimal() {
        List<LandlordReview> threeReviews = List.of(review(5), review(5), review(3));
        when(reviewRepository.countApprovedByTenantId(landlordTenantId)).thenReturn(3L);
        when(reviewRepository.findApprovedByTenantId(landlordTenantId)).thenReturn(threeReviews);

        ReviewSummaryResponse summary = service.getSummary(landlordTenantId);

        assertEquals(4.3, summary.averageRating());
    }

    @Test
    void pendingAndHiddenReviewsNeverFeedTheSummary() {
        when(reviewRepository.countApprovedByTenantId(landlordTenantId)).thenReturn(2L);
        when(reviewRepository.findApprovedByTenantId(landlordTenantId))
                .thenReturn(List.of(review(5), review(1)));

        ReviewSummaryResponse summary = service.getSummary(landlordTenantId);

        assertEquals(2, summary.reviewCount());
        assertNull(summary.averageRating());
        assertFalse(summary.averageShown());
        verify(reviewRepository, never()).findByTenantId(any());
    }

    @Test
    void getReviewsEnrichesRenterName_whenProfileExists() {
        UUID profileId = UUID.randomUUID();
        LandlordReview r = LandlordReview.rehydrate(
                UUID.randomUUID(), landlordTenantId, profileId, UUID.randomUUID(),
                5, "Great", 0L, null, null);
        when(reviewRepository.findByTenantId(landlordTenantId)).thenReturn(List.of(r));

        TenantProfile profile = mock(TenantProfile.class);
        when(profile.getFullName()).thenReturn("Jane Wanjiku");
        when(tenantProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));

        var response = service.getReviews(landlordTenantId);

        assertEquals(1, response.size());
        assertEquals("Jane Wanjiku", response.get(0).renterName());
        assertEquals(5, response.get(0).rating());
        assertEquals("Great", response.get(0).comment());
    }

    @Test
    void getReviewsToleratesMissingProfile() {
        UUID profileId = UUID.randomUUID();
        LandlordReview r = LandlordReview.rehydrate(
                UUID.randomUUID(), landlordTenantId, profileId, UUID.randomUUID(),
                4, "OK", 0L, null, null);
        when(reviewRepository.findByTenantId(landlordTenantId)).thenReturn(List.of(r));
        when(tenantProfileRepository.findById(any())).thenReturn(Optional.empty());

        var response = service.getReviews(landlordTenantId);

        assertEquals(1, response.size());
        assertNull(response.get(0).renterName());
    }

    @Test
    void getStatusCountsReportsEachState() {
        when(reviewRepository.countApprovedByTenantId(landlordTenantId)).thenReturn(2L);
        when(reviewRepository.countByTenantIdAndStatus(landlordTenantId, ReviewStatus.PENDING)).thenReturn(3L);
        when(reviewRepository.countByTenantIdAndStatus(landlordTenantId, ReviewStatus.HIDDEN)).thenReturn(1L);

        var counts = service.getStatusCounts(landlordTenantId);

        assertEquals(2L, counts.approvedCount());
        assertEquals(3L, counts.pendingCount());
        assertEquals(1L, counts.hiddenCount());
    }

    private LandlordReview review(int rating) {
        return LandlordReview.rehydrate(
                UUID.randomUUID(), landlordTenantId, UUID.randomUUID(), UUID.randomUUID(),
                rating, null, 0L, null, null);
    }
}
