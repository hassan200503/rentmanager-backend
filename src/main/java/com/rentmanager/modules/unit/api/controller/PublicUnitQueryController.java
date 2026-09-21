package com.rentmanager.modules.unit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.review.application.ReviewPublicQueryService;
import com.rentmanager.modules.review.application.dto.response.PublicLandlordReviewsResponse;
import com.rentmanager.modules.unit.application.dto.response.PublicUnitResponse;
import com.rentmanager.modules.unit.application.dto.response.UnitReservationSummaryResponse;
import com.rentmanager.modules.unit.application.query.service.PublicUnitQueryService;
import com.rentmanager.modules.unit.application.query.service.UnitReservationSummaryQueryService;
import com.rentmanager.shared.web.PublicCacheControl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Everything a renter can browse without signing in.
 *
 * <p>Every answer here is the same for every visitor, so each carries
 * {@link PublicCacheControl#catalogue()}: listings are read far more often
 * than they change, and the free-tier instance should not recompute the same
 * page of results for each visitor. Nothing person-specific is served from
 * this controller — a reservation's status lives elsewhere, uncached, for
 * exactly that reason.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/units")
public class PublicUnitQueryController {

    private final PublicUnitQueryService publicUnitQueryService;
    private final UnitReservationSummaryQueryService unitReservationSummaryQueryService;
    private final ReviewPublicQueryService reviewPublicQueryService;

    /**
     * Public renter search.
     *
     * <p>Until now this took only a free-text {@code keyword} matched against
     * unit number and description — so there was no way to search by where a
     * place is, or what it costs. Location and budget are the first two
     * questions a renter asks.
     *
     * <p>Every parameter is optional; omitting all of them browses everything
     * publicly visible.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PublicUnitResponse>>> getVacantUnits(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) java.math.BigDecimal minRent,
            @RequestParam(required = false) java.math.BigDecimal maxRent,
            @RequestParam(required = false) com.rentmanager.modules.property.domain.enums.PropertyType propertyType,
            Pageable pageable
    ) {
        return ResponseEntity.ok()
                .cacheControl(PublicCacheControl.catalogue())
                .body(ApiResponse.ok(
                        "Vacant units retrieved successfully",
                        publicUnitQueryService.getVacantUnits(
                                keyword, city, minRent, maxRent, propertyType, pageable)
                ));
    }

    @GetMapping("/property/{propertyId}")
    public ResponseEntity<ApiResponse<Page<PublicUnitResponse>>> getVacantUnitsByProperty(
            @PathVariable UUID propertyId,
            Pageable pageable
    ) {
        return ResponseEntity.ok()
                .cacheControl(PublicCacheControl.catalogue())
                .body(ApiResponse.ok(
                        "Property vacant units retrieved successfully",
                        publicUnitQueryService.getVacantUnitsByProperty(propertyId, pageable)
                ));
    }

    @GetMapping("/{unitId}")
    public ResponseEntity<ApiResponse<PublicUnitResponse>> getVacantUnit(
            @PathVariable UUID unitId
    ) {
        return ResponseEntity.ok()
                .cacheControl(PublicCacheControl.catalogue())
                .body(ApiResponse.ok(
                        "Unit retrieved successfully",
                        publicUnitQueryService.getVacantUnitById(unitId)
                ));
    }

    @GetMapping("/featured/longest-vacant")
    public ResponseEntity<ApiResponse<PublicUnitResponse>> getLongestVacantUnit() {
        return ResponseEntity.ok()
                .cacheControl(PublicCacheControl.catalogue())
                .body(ApiResponse.ok(
                        "Longest vacant unit retrieved successfully",
                        publicUnitQueryService.getLongestVacantUnit()
                ));
    }

    /**
     * Returns unit details needed by the reservation form.
     * Public — no auth required.
     *
     * GET /api/v1/public/units/{unitId}/summary
     */
    @GetMapping("/{unitId}/summary")
    public ResponseEntity<ApiResponse<UnitReservationSummaryResponse>> getUnitReservationSummary(
            @PathVariable UUID unitId
    ) {
        return ResponseEntity.ok()
                .cacheControl(PublicCacheControl.catalogue())
                .body(ApiResponse.ok(
                        "Unit summary retrieved successfully",
                        unitReservationSummaryQueryService.getSummary(unitId)
                ));
    }

    /**
     * Public landlord reviews for this unit's landlord (Phase 4b).
     * Resolution is server-side from the already-public unit id, and only
     * for a publicly visible (active, vacant) unit - private listings never
     * leak review data. Renter names are redacted to first names.
     */
    @GetMapping("/{unitId}/reviews")
    public ResponseEntity<ApiResponse<PublicLandlordReviewsResponse>> getUnitReviews(
            @PathVariable UUID unitId
    ) {
        return ResponseEntity.ok()
                .cacheControl(PublicCacheControl.catalogue())
                .body(ApiResponse.ok(
                        "Reviews retrieved successfully",
                        reviewPublicQueryService.getForUnit(unitId)
                ));
    }
}
