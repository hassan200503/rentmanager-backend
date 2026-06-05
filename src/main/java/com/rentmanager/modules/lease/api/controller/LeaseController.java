package com.rentmanager.modules.lease.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.contract.common.PageResponse;
import com.rentmanager.contract.lease.request.*;
import com.rentmanager.contract.lease.response.*;
import com.rentmanager.modules.lease.application.service.LeaseApplicationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;



@RestController
@RequestMapping("/api/v1/leases")
public class LeaseController {

    private final LeaseApplicationService leaseService;

    public LeaseController(LeaseApplicationService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    public ApiResponse<LeaseResponse> create(@Valid @RequestBody CreateLeaseRequest request) {
        return ApiResponse.ok(leaseService.create(request));
    }

    @PutMapping("/{leaseId}")
    public ApiResponse<LeaseResponse> update(
            @PathVariable UUID leaseId,
            @Valid @RequestBody UpdateLeaseRequest request
    ) {
        return ApiResponse.ok(leaseService.update(leaseId, request));
    }


    @GetMapping("/{leaseId}")
    public ApiResponse<LeaseDetailResponse> getById(
            @PathVariable UUID leaseId
    ) {
        return ApiResponse.ok(leaseService.getById(leaseId));
    }

    @PostMapping("/{leaseId}/action")
    public ApiResponse<LeaseActionResponse> action(
            @PathVariable UUID leaseId,
            @Valid @RequestBody LeaseActionRequest request
    ) {
        return ApiResponse.ok(leaseService.executeAction(leaseId, request));
    }

    @DeleteMapping("/{leaseId}")
    public ApiResponse<Void> delete(@PathVariable UUID leaseId) {
        leaseService.delete(leaseId);
        return ApiResponse.ok("Deleted successfully", null);
    }


}