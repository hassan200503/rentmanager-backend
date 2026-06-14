package com.rentmanager.ai.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.ai.application.AiUsageService;
import com.rentmanager.ai.api.dto.AiUsageResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/usage")
public class AiUsageController {

    private final AiUsageService service;

    public AiUsageController(AiUsageService service) {
        this.service = service;
    }

    /**
     * Tenant-level AI usage stats (tokens, requests, cost)
     */
    @GetMapping
    public ApiResponse<List<AiUsageResponse>> getUsage() {
        return ApiResponse.ok(
                service.getTenantUsage()
                        .stream()
                        .map(AiUsageResponse::from)
                        .toList()
        );
    }
}