package com.rentmanager.modules.review.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.review.application.PlatformReviewQueryService;
import com.rentmanager.modules.review.application.dto.response.PlatformTestimonialResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public platform testimonials feed (V65): the latest approved reviews in
 * both directions — renters on landlords and landlords on renters — for
 * the marketing/landing surface.
 *
 * <p>Strictly unauthenticated and deliberately free of tenant context:
 * only {@code APPROVED} reviews are eligible and reviewer names are
 * redacted to first names server-side.</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/public/testimonials")
public class PublicTestimonialsController {

    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 30;

    private final PlatformReviewQueryService queryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlatformTestimonialResponse>>> getTestimonials(
            @RequestParam(defaultValue = "8") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Testimonials retrieved",
                queryService.getTestimonials(Math.min(Math.max(limit, 1), MAX_LIMIT))));
    }
}