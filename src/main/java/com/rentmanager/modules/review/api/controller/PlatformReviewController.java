package com.rentmanager.modules.review.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.review.api.dto.SubmitPlatformReviewRequest;
import com.rentmanager.modules.review.application.PlatformReviewCommandService;
import com.rentmanager.modules.review.application.dto.response.PlatformReviewResponse;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform review submission surface (V66): any authenticated user —
 * landlord or tenant — rates the platform itself. {@code GET /me}
 * returns the user's own review, or {@code data: null} if they have not
 * submitted one yet.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform-reviews")
public class PlatformReviewController {

    private final PlatformReviewCommandService commandService;

    @PostMapping
    public ResponseEntity<ApiResponse<PlatformReviewResponse>> submit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SubmitPlatformReviewRequest request
    ) {
        PlatformReviewResponse response = commandService.submit(
                user.getUserId(), request.rating(), request.comment());
        return ResponseEntity.ok(ApiResponse.ok(
                "Thanks, your review of RentManager was received", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PlatformReviewResponse>> getMyReview(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        PlatformReviewResponse response = commandService.getMyReview(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("My review retrieved", response));
    }
}