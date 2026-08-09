package com.rentmanager.modules.platformadmin.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.review.application.PlatformReviewQueryService;
import com.rentmanager.modules.review.application.ReviewModerationService;
import com.rentmanager.modules.review.application.dto.response.PlatformReviewResponse;
import com.rentmanager.modules.review.application.dto.response.PlatformStatsResponse;
import com.rentmanager.modules.review.domain.enums.ReviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Platform-wide review moderation (V65), both directions of every
 * tenancy: renter -> landlord and landlord -> renter.
 *
 * <p>Every endpoint here is guarded by the platform authorities derived
 * from the Clerk {@code platformRole} claim — landlord and renter tokens
 * are always rejected, regardless of frontend route guards. Moderation
 * is operational content work (not money-governing), so both staff
 * {@code ROLE_PLATFORM_ADMIN} and {@code ROLE_PLATFORM_OWNER} may act;
 * statistical reads are open to the same two roles.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
public class PlatformAdminReviewController {

    private static final int MAX_LIMIT = 50;

    private final PlatformReviewQueryService queryService;
    private final ReviewModerationService moderationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlatformReviewResponse>>> list(
            @RequestParam(defaultValue = "PENDING") ReviewStatus status,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Reviews retrieved",
                queryService.listByStatus(status, boundedLimit(limit))));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PlatformStatsResponse>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Review moderation stats retrieved",
                queryService.getStats()));
    }

    @PatchMapping("/{type}/{reviewId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable ReviewModerationService.ReviewType type,
            @PathVariable UUID reviewId
    ) {
        moderationService.approve(type, reviewId);
        return ResponseEntity.ok(ApiResponse.ok("Review approved", null));
    }

    @PatchMapping("/{type}/{reviewId}/hide")
    public ResponseEntity<ApiResponse<Void>> hide(
            @PathVariable ReviewModerationService.ReviewType type,
            @PathVariable UUID reviewId
    ) {
        moderationService.hide(type, reviewId);
        return ResponseEntity.ok(ApiResponse.ok("Review hidden", null));
    }

    private static int boundedLimit(int requested) {
        return Math.min(Math.max(requested, 1), MAX_LIMIT);
    }
}