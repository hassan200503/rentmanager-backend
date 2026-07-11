package com.rentmanager.modules.lease.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.contract.common.PageResponse;
import com.rentmanager.modules.lease.application.dto.request.*;
import com.rentmanager.modules.lease.application.dto.response.*;
import com.rentmanager.modules.lease.application.service.LeaseApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * RBAC (added this session, per Addendum 2 §4.1 resolution):
 *
 * create/update: OWNER+MANAGER -- creating a lease or editing its
 * contractual/financial terms (rent, deposit, dates) is treated at the same
 * stakes tier as RentLedger's money-moving endpoints, not RentLedger's
 * ordinary writes -- STAFF is excluded.
 *
 * getById: OWNER+MANAGER+STAFF -- read access, needed operationally by all
 * three roles (e.g. STAFF checking a tenant's rent due date).
 *
 * executeAction: OWNER+MANAGER. This single endpoint bundles six distinct
 * transitions (APPROVE, AWAITING_DEPOSIT, ACTIVATE, REJECT, TERMINATE,
 * RENEW) behind one request-body enum -- @PreAuthorize gates the method,
 * not the enum value, so it cannot apply a stricter rule to TERMINATE
 * alone without splitting this into separate endpoints. FLAGGED, NOT
 * FIXED: TERMINATE arguably deserves a stricter gate than RENEW/APPROVE;
 * that split is an endpoint-shape/architecture change out of scope for
 * this RBAC pass. Uniform OWNER+MANAGER is the safe default for now.
 *
 * delete: OWNER ONLY. Deliberately the single strictest gate applied in
 * this sweep across Property/Lease/Unit -- outright deletion is more
 * consequential than any status transition executeAction can produce, and
 * unlike those transitions it cannot be reversed by a subsequent action.
 * Mirrors TenantController's OWNER-only tier (suspend/Daraja-config),
 * not UserController's OWNER+MANAGER tier.
 *
 * KNOWN GAP, NOT FIXED HERE: LeaseApplicationService.delete() calls
 * leaseRepository.delete(...) with no pullDomainEvents()/publishAll()
 * afterward. If Lease registers any event on deletion it is silently
 * discarded -- same bug class as the original event-publish sweep
 * (Addendum 2 §1-2). Out of scope for this RBAC-only change; flagged for
 * the findings list.
 */
@RestController
@RequestMapping("/api/v1/leases")
public class LeaseController {

    private final LeaseApplicationService leaseService;

    public LeaseController(LeaseApplicationService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<LeaseResponse> create(@Valid @RequestBody CreateLeaseRequest request) {
        return ApiResponse.ok(leaseService.create(request));
    }

    @PutMapping("/{leaseId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<LeaseResponse> update(
            @PathVariable UUID leaseId,
            @Valid @RequestBody UpdateLeaseRequest request
    ) {
        return ApiResponse.ok(leaseService.update(leaseId, request));
    }


    @GetMapping("/{leaseId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ApiResponse<LeaseDetailResponse> getById(
            @PathVariable UUID leaseId
    ) {
        return ApiResponse.ok(leaseService.getById(leaseId));
    }

    @PostMapping("/{leaseId}/action")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<LeaseActionResponse> action(
            @PathVariable UUID leaseId,
            @Valid @RequestBody LeaseActionRequest request
    ) {
        return ApiResponse.ok(leaseService.executeAction(leaseId, request));
    }

    @DeleteMapping("/{leaseId}")
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    public ApiResponse<Void> delete(@PathVariable UUID leaseId) {
        leaseService.delete(leaseId);
        return ApiResponse.ok("Deleted successfully", null);
    }


}