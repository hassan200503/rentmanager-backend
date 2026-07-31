package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.tenant.api.routes.TenantRoutes;
import com.rentmanager.modules.tenant.application.command.service.SubscriptionPlanCommandService;
import com.rentmanager.modules.tenant.application.query.service.SubscriptionPlanQueryService;
import com.rentmanager.modules.tenant.application.dto.request.SubscriptionPlanRequest;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionPlanResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping(TenantRoutes.SUBSCRIPTION_PLANS)
public class SubscriptionPlanController {

    private final SubscriptionPlanCommandService commandService;
    private final SubscriptionPlanQueryService queryService;

    public SubscriptionPlanController(
            SubscriptionPlanCommandService commandService,
            SubscriptionPlanQueryService queryService
    ) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> create(@RequestBody SubscriptionPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(commandService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionPlanResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getAll()));
    }

    @GetMapping(TenantRoutes.BY_ID)
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getById(id)));
    }

    @GetMapping(TenantRoutes.BY_CODE)
    public ResponseEntity<ApiResponse<SubscriptionPlanResponse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(queryService.getByCode(code)));
    }

    @PutMapping(TenantRoutes.DEACTIVATE)
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable UUID id) {
        commandService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.ok("Plan deactivated", null));
    }
}