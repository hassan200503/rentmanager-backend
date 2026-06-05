package com.rentmanager.modules.tenant.api.controller;

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
    public ResponseEntity<SubscriptionPlanResponse> create(@RequestBody SubscriptionPlanRequest request) {
        return ResponseEntity.ok(commandService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionPlanResponse>> getAll() {
        return ResponseEntity.ok(queryService.getAll());
    }

    @GetMapping(TenantRoutes.BY_ID)
    public ResponseEntity<SubscriptionPlanResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.getById(id));
    }

    @GetMapping(TenantRoutes.BY_CODE)
    public ResponseEntity<SubscriptionPlanResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(queryService.getByCode(code));
    }

    @PutMapping(TenantRoutes.DEACTIVATE)
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        commandService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}