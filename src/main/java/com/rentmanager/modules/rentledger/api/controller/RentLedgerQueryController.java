package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.response.LeaseBalanceSummaryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerSummaryResponse;
import com.rentmanager.modules.rentledger.application.dto.RentLedgerSummary;
import com.rentmanager.modules.rentledger.application.service.RentLedgerSummaryService;
import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerEntryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentTransactionResponse;
import com.rentmanager.modules.rentledger.api.dto.response.RentTransactionSummaryResponse;
import com.rentmanager.modules.rentledger.application.query.service.RentLedgerQueryService;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * No pagination: findByLease, findByTenantAndStatus, and (as of this
 * session) findByLedgerEntry all return List, not Page, per the underlying
 * repository signatures. Flagging as a known scale gap consistent with the
 * rest of Phase 1 — not retrofitting an assumed Pageable signature that
 * doesn't exist on the repository today.
 *
 * RBAC: gated OWNER+MANAGER+STAFF on all four endpoints, matching
 * recordTransaction's tier on the command side — STAFF has an operational
 * need to view ledger entries and transactions (they're often the one
 * recording payments); the higher-risk actions (adjustments, overpayment
 * resolution) remain locked to OWNER+MANAGER on the command controller,
 * unaffected by this.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rent-ledger")
public class RentLedgerQueryController {

    private final RentLedgerQueryService rentLedgerQueryService;
    private final RentLedgerSummaryService rentLedgerSummaryService;

    /**
     * The landlord dashboard's financial figures: collected this month, total
     * outstanding, and the overdue slice with its entry count.
     *
     * <p>Readable by STAFF as well as OWNER and MANAGER, matching the rest of
     * this controller — a caretaker who records payments needs to see what is
     * still owed. Nothing here exposes an individual renter's details, only
     * portfolio totals.
     *
     * <p>Every figure is aggregated in the database and scoped to the caller's
     * tenant from the verified JWT. This endpoint exists because the dashboard
     * had six financial cards built from a hard-coded array — including a
     * green "+12%" beside "KES 0" — and no aggregate anywhere to wire them to.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<RentLedgerSummaryResponse>> getSummary(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        RentLedgerSummary summary =
                rentLedgerSummaryService.getSummary(requireTenantId(user));

        return ResponseEntity.ok(ApiResponse.ok(
                "Rent ledger summary retrieved",
                RentLedgerSummaryResponse.from(summary)));
    }

    /**
     * The Tenants page's rent-status column and stat cards: one entry per
     * lease that currently has money outstanding or an unresolved
     * overpayment, computed from one query across the whole tenant rather
     * than one ledger call per lease row.
     */
    @GetMapping("/balance-by-lease")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<LeaseBalanceSummaryResponse>>> getBalanceByLease(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<LeaseBalanceSummaryResponse> response =
                rentLedgerQueryService.getBalanceByLease(requireTenantId(user));

        return ResponseEntity.ok(
                ApiResponse.ok("Lease balances retrieved successfully", response)
        );
    }

    @GetMapping("/entries/{entryId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
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
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
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

    @GetMapping("/entries/{entryId}/transactions")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<RentTransactionResponse>>> getTransactionsForEntry(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID entryId
    ) {
        List<RentTransactionResponse> response =
                rentLedgerQueryService.getTransactionsForEntry(requireTenantId(user), entryId);

        return ResponseEntity.ok(
                ApiResponse.ok("Rent ledger transactions retrieved successfully", response)
        );
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<RentTransactionSummaryResponse>>> getAllTransactions(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<RentTransactionSummaryResponse> response =
                rentLedgerQueryService.getAllTransactions(requireTenantId(user));

        return ResponseEntity.ok(
                ApiResponse.ok("All transactions retrieved successfully", response)
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