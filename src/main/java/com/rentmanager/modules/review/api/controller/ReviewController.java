package com.rentmanager.modules.review.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.review.application.ReviewQueryService;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.shared.security.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Landlord-facing reviews (Phase 4b). Tenant identity comes ONLY from
 * TenantContext (JWT) - reviews are tenant-scoped, never client-supplied.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reviews")
@PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
public class ReviewController {

    private final ReviewQueryService queryService;

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
