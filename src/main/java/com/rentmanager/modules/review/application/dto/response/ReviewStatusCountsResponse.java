package com.rentmanager.modules.review.application.dto.response;

/**
 * Moderation state counts for one tenant's reviews — lets the dashboard
 * badge pending approvals and hidden content without loading the list.
 */
public record ReviewStatusCountsResponse(
        long approvedCount,
        long pendingCount,
        long hiddenCount
) {
}