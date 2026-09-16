package com.rentmanager.modules.review.application;

import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import com.rentmanager.modules.review.domain.model.RenterReview;
import com.rentmanager.modules.review.domain.repository.RenterReviewRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RenterReviewQueryServiceTest {

    private static final UUID LANDLORD_TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_A = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PROFILE_B = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private final RenterReviewRepository reviewRepository = mock(RenterReviewRepository.class);
    private final TenantProfileRepository tenantProfileRepository = mock(TenantProfileRepository.class);
    private final RenterReviewQueryService service = new RenterReviewQueryService(reviewRepository, tenantProfileRepository);

    private RenterReview review(UUID profileId, int rating) {
        return RenterReview.rehydrate(
                UUID.randomUUID(),
                LANDLORD_TENANT_ID,
                profileId,
                UUID.randomUUID(),
                rating,
                "comment",
                ReviewStatus.APPROVED,
                0L,
                java.time.Instant.now(),
                java.time.Instant.now()
        );
    }

    @BeforeEach
    void setUp() {
        when(reviewRepository.findApprovedByTenantId(LANDLORD_TENANT_ID))
                .thenReturn(List.of(
                        review(PROFILE_A, 4),
                        review(PROFILE_A, 5),
                        review(PROFILE_A, 5),
                        review(PROFILE_B, 1),
                        review(PROFILE_B, 2)
                ));
    }

    @Test
    void summaryIsScopedToTheRenterProfile() {
        ReviewSummaryResponse summary = service.getSummaryForProfile(LANDLORD_TENANT_ID, PROFILE_A);

        assertEquals(3, summary.reviewCount());
        assertEquals(4.7, summary.averageRating());
        assertTrue(summary.averageShown());
    }

    @Test
    void averageHiddenBelowThreeApprovedReviews() {
        ReviewSummaryResponse summary = service.getSummaryForProfile(LANDLORD_TENANT_ID, PROFILE_B);

        assertEquals(2, summary.reviewCount());
        assertNull(summary.averageRating());
        assertFalse(summary.averageShown());
    }

    @Test
    void unknownProfileGetsAnEmptySummary() {
        ReviewSummaryResponse summary = service.getSummaryForProfile(LANDLORD_TENANT_ID, UUID.randomUUID());

        assertEquals(0, summary.reviewCount());
        assertNull(summary.averageRating());
        assertFalse(summary.averageShown());
    }

    @Test
    void nullProfileGetsAnEmptySummary() {
        ReviewSummaryResponse summary = service.getSummaryForProfile(LANDLORD_TENANT_ID, null);

        assertEquals(0, summary.reviewCount());
        assertNull(summary.averageRating());
        assertFalse(summary.averageShown());
    }

    /**
     * Previously untested: ReviewController#getCounts() silently omitted
     * this method's result entirely (only the received-review counts from
     * ReviewQueryService were exposed), so the dashboard's "Moderation
     * state" card never reflected a landlord's own reviews of their
     * renters. Mirrors ReviewQueryServiceTest#getStatusCountsReportsEachState
     * for the received side.
     */
    @Test
    void getStatusCountsReportsEachState() {
        when(reviewRepository.countApprovedByTenantId(LANDLORD_TENANT_ID)).thenReturn(4L);
        when(reviewRepository.countByTenantIdAndStatus(LANDLORD_TENANT_ID, ReviewStatus.PENDING)).thenReturn(2L);
        when(reviewRepository.countByTenantIdAndStatus(LANDLORD_TENANT_ID, ReviewStatus.HIDDEN)).thenReturn(1L);

        var counts = service.getStatusCounts(LANDLORD_TENANT_ID);

        assertEquals(4L, counts.approvedCount());
        assertEquals(2L, counts.pendingCount());
        assertEquals(1L, counts.hiddenCount());
    }
}