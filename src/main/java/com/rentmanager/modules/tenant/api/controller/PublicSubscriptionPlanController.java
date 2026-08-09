package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;
import com.rentmanager.modules.tenant.application.query.service.SubscriptionPlanQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public pricing catalog for unauthenticated surfaces (landing page,
 * marketing site, checkout previews). Serves only ACTIVE plans so a plan
 * deactivated by the platform owner disappears from public pricing the
 * moment it is deactivated.
 *
 * <p>The path is under /api/v1/public/** which SecurityConfig marks
 * permitAll, so this is intentionally a read-only projection: attempts to
 * reprice through this controller are impossible by construction.</p>
 *
 * GET /api/v1/public/subscription-plans
 */
@RestController
@RequestMapping("/api/v1/public/subscription-plans")
public class PublicSubscriptionPlanController {

    private final SubscriptionPlanQueryService queryService;

    public PublicSubscriptionPlanController(SubscriptionPlanQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> getActivePlans() {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getActive()));
    }

}