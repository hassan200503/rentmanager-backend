package com.rentmanager.modules.review.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.review.application.RenterReviewCommandService;
import com.rentmanager.modules.review.application.RenterReviewQueryService;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.review.application.dto.response.ReviewStatusCountsResponse;
import com.rentmanager.shared.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The dashboard's "Moderation state" card must cover both directions of a
 * landlord's renter reviews — reviews RECEIVED from renters (ReviewQueryService,
 * backed by LandlordReview) and reviews GIVEN to renters
 * (RenterReviewQueryService, backed by RenterReview). Before this fix,
 * {@code getCounts()} returned only the received side, so a landlord with a
 * pending or hidden review of one of their renters saw it counted nowhere on
 * the card, even though the same review appeared correctly, with its real
 * status, in the dashboard's own "Given" tab.
 */
class ReviewControllerTest {

    private ReviewQueryService queryService;
    private RenterReviewCommandService renterReviewCommandService;
    private RenterReviewQueryService renterReviewQueryService;
    private ReviewController controller;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = mock(ReviewQueryService.class);
        renterReviewCommandService = mock(RenterReviewCommandService.class);
        renterReviewQueryService = mock(RenterReviewQueryService.class);
        controller = new ReviewController(queryService, renterReviewCommandService, renterReviewQueryService);
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void countsCombineReceivedAndGivenReviews() {
        when(queryService.getStatusCounts(tenantId))
                .thenReturn(new ReviewStatusCountsResponse(1, 0, 0));
        when(renterReviewQueryService.getStatusCounts(tenantId))
                .thenReturn(new ReviewStatusCountsResponse(0, 2, 1));

        ApiResponse<ReviewStatusCountsResponse> response = controller.getCounts().getBody();

        ReviewStatusCountsResponse counts = response.data();
        assertEquals(1, counts.approvedCount());
        assertEquals(2, counts.pendingCount());
        assertEquals(1, counts.hiddenCount());
    }

    @Test
    void countsAreZero_whenNeitherDirectionHasReviews() {
        when(queryService.getStatusCounts(tenantId))
                .thenReturn(new ReviewStatusCountsResponse(0, 0, 0));
        when(renterReviewQueryService.getStatusCounts(tenantId))
                .thenReturn(new ReviewStatusCountsResponse(0, 0, 0));

        ReviewStatusCountsResponse counts = controller.getCounts().getBody().data();

        assertEquals(0, counts.approvedCount());
        assertEquals(0, counts.pendingCount());
        assertEquals(0, counts.hiddenCount());
    }
}
