package com.rentmanager.ai.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.ai.application.AiInsightService;
import com.rentmanager.ai.api.dto.AiInsightResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/insights")
public class AiInsightController {

    private final AiInsightService service;

    public AiInsightController(AiInsightService service) {
        this.service = service;
    }

    /**
     * Get all AI insights for current tenant
     * SaaS-safe: tenant resolved from TenantContext internally
     */
    @GetMapping
    public ApiResponse<List<AiInsightResponse>> getInsights() {
        return ApiResponse.ok(
                service.getTenantInsights()
                        .stream()
                        .map(AiInsightResponse::from)
                        .toList()
        );
    }

    /**
     * Get insights for a specific entity (lease/property/unit)
     */
    @GetMapping("/entity/{entityId}")
    public ApiResponse<List<AiInsightResponse>> getByEntity(
            @PathVariable UUID entityId
    ) {
        return ApiResponse.ok(
                service.getInsightsByEntity(entityId)
                        .stream()
                        .map(AiInsightResponse::from)
                        .toList()
        );
    }
}