package com.rentmanager.modules.review.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.review.api.dto.SubmitRenterReviewRequest;
import com.rentmanager.modules.review.application.RenterReviewCommandService;
import com.rentmanager.modules.review.application.RenterReviewQueryService;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.RenterReviewResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewStatusCountsResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.shared.security.context.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Landlord-facing reviews (Phase 4b + V65 bidirectional). Tenant identity
 * comes ONLY from TenantContext (JWT) - reviews are tenant-scoped, never
 * client-supplied. Submission of a landlord -> renter review (V65) falls
 * on the same guarded surface so the landlord dashboard can review its
 * own renters.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
@PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
public class ReviewController {

    private final ReviewQueryService queryService;
    private final RenterReviewCommandService renterReviewCommandService;
    private final RenterReviewQueryService renterReviewQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LandlordReviewResponse>>> getReviews() {
        UUID tenantId = resolveStrictTenantId();
        return ResponseEntity.ok(ApiResponse.ok(
                "Reviews retrieved",
                queryService.getReviews(tenantId)));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> getSummary() {
        UUID tenantId = resolveStrictTenantId();
        return ResponseEntity.ok(ApiResponse.ok(
                "Review summary retrieved",
                queryService.getSummary(tenantId)));
    }

    /**
     * The landlord's reviews of its own renters (V65) — everything with
     * status badges so the dashboard can show moderation state, mirroring
     * {@link #getReviews()} for the other direction.
     */
    @GetMapping("/renter")
    public ResponseEntity<ApiResponse<List<RenterReviewResponse>>> getRenterReviews() {
        UUID tenantId = resolveStrictTenantId();
        return ResponseEntity.ok(ApiResponse.ok(
                "Renter reviews retrieved",
                renterReviewQueryService.getReviews(tenantId)));
    }

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<ReviewStatusCountsResponse>> getCounts() {
        UUID tenantId = resolveStrictTenantId();
        return ResponseEntity.ok(ApiResponse.ok(
                "Review status counts retrieved",
                queryService.getStatusCounts(tenantId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RenterReviewResponse>> submitRenterReview(
            @Valid @RequestBody SubmitRenterReviewRequest request
    ) {
        UUID tenantId = resolveStrictTenantId();
        renterReviewCommandService.submit(
                tenantId, request.tenantProfileId(), request.rating(), request.comment());
        RenterReviewResponse response = renterReviewQueryService
                .getReviewForProfile(tenantId, request.tenantProfileId());
        return ResponseEntity.ok(ApiResponse.ok("Review submitted", response));
    }

    private UUID resolveStrictTenantId() {
        UUID tenantId;
        try {
            tenantId = TenantContext.getTenantId();
        } catch (IllegalStateException e) {
            throw new AccessDeniedException(
                    "Tenant context could not be resolved for this request", e);
        }
        if (tenantId == null) {
            throw new AccessDeniedException(
                    "Tenant context could not be resolved for this request");
        }
        return tenantId;
    }
}