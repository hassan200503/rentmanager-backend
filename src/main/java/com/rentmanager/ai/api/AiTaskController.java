package com.rentmanager.ai.api;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.ai.application.AiTaskOrchestrator;
import com.rentmanager.ai.api.dto.AiTaskRequest;
import com.rentmanager.ai.api.dto.AiTaskResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/task")
public class AiTaskController {

    private final AiTaskOrchestrator orchestrator;

    public AiTaskController(AiTaskOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    public ApiResponse<AiTaskResponse> execute(
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestBody AiTaskRequest request
    ) {

        String result = orchestrator.execute(
                tenantId,
                request.query()
        );

        return ApiResponse.ok(
                AiTaskResponse.from(result)
        );
    }
}