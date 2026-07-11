package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerEntryResponse;
import com.rentmanager.modules.rentledger.application.query.service.RentLedgerQueryService;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Query-side endpoints for the rent ledger, mirroring PropertyQueryController's
 * convention: ResponseEntity<ApiResponse<T>> return type (distinct from the
 * command controller's bare ApiResponse<T>), requireTenantId() duplicated
 * locally rather than shared via a base class, since that's the established
 * pattern in PropertyQueryController rather than an invented abstraction.
 *
 * getByStatus takes RentLedgerStatus directly as a @PathVariable — Spring's
 * built-in enum converter handles this — rather than String + manual parsing,
 * since RentLedgerEntryRepository.findByTenantAndStatus is itself typed to
 * RentLedgerStatus, not String (unlike Property's findByStatusAndTenantId).
 *
 * No pagination: findByLease and findByTenantAndStatus both return List, not
 * Page, per RentLedgerEntryRepository's actual signatures. Flagging as a
 * known scale gap consistent with the rest of Phase 1 — a lease accumulating
 * many months of entries, or a landlord with many overdue entries, will
 * eventually want this paginated. Not retrofitting an assumption Pageable
 * signature that doesn't exist on the repository today.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rent-ledger")
public class RentLedgerQueryController {

    private final RentLedgerQueryService rentLedgerQueryService;

    @GetMapping("/entries/{entryId}")
    public ResponseEntity<ApiResponse<RentLedgerEntryResponse>> getById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID entryId
    ) {
        RentLedgerEntryResponse response =
                rentLedgerQueryService.getById(requireTenantId(user), entryId);

        return ResponseEntity.ok(
                ApiResponse.ok("Rent ledger entry retrieved successfully", response)
        );
    }

    @GetMapping("/leases/{leaseId}/entries")
    public ResponseEntity<ApiResponse<List<RentLedgerEntryResponse>>> getByLease(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID leaseId
    ) {
        List<RentLedgerEntryResponse> response =
                rentLedgerQueryService.getByLease(requireTenantId(user), leaseId);

        return ResponseEntity.ok(
                ApiResponse.ok("Lease ledger entries retrieved successfully", response)
        );
    }

    @GetMapping("/entries/status/{status}")
    public ResponseEntity<ApiResponse<List<RentLedgerEntryResponse>>> getByStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable RentLedgerStatus status
    ) {
        List<RentLedgerEntryResponse> response =
                rentLedgerQueryService.getByStatus(requireTenantId(user), status);

        return ResponseEntity.ok(
                ApiResponse.ok("Rent ledger entries retrieved successfully", response)
        );
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }
}