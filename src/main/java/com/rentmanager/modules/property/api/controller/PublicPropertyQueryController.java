package com.rentmanager.modules.property.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.property.application.dto.response.PublicPropertyResponse;
import com.rentmanager.modules.property.application.query.service.PublicPropertyQueryService;
import com.rentmanager.modules.review.application.ReviewPublicQueryService;
import com.rentmanager.modules.review.application.dto.response.PublicLandlordReviewsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/properties")
public class PublicPropertyQueryController {

    private final PublicPropertyQueryService publicPropertyQueryService;
    private final ReviewPublicQueryService reviewPublicQueryService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicPropertyResponse>>> getProperties(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String location,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Properties retrieved successfully",
                        publicPropertyQueryService.getProperties(keyword, location, pageable)
                )
        );
    }

    @GetMapping("/{propertyId}")
    public ResponseEntity<ApiResponse<PublicPropertyResponse>> getProperty(
            @PathVariable UUID propertyId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Property retrieved successfully",
                        publicPropertyQueryService.getProperty(propertyId)
                )
        );
    }

    /**
     * Public landlord reviews for this property's landlord (Phase 4b).
     * Resolution is server-side from the already-public property id, and
     * only for an ACTIVE property - private listings never leak review
     * data. Renter names are redacted to first names.
     */
    @GetMapping("/{propertyId}/reviews")
    public ResponseEntity<ApiResponse<PublicLandlordReviewsResponse>> getPropertyReviews(
            @PathVariable UUID propertyId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        "Reviews retrieved successfully",
                        reviewPublicQueryService.getForProperty(propertyId)
                )
        );
    }
}