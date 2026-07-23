package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.request.InitiateRentPaymentRequest;
import com.rentmanager.modules.rentledger.api.dto.response.RentPaymentRequestResponse;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Landlord-triggered M-Pesa collection against an outstanding
 * {@code RentLedgerEntry} — the authenticated half of the rent-payment
 * flow. The public, secret-gated Daraja callback lives separately in
 * {@code RentPaymentCallbackController}, since it sits under a completely
 * different security zone (/api/v1/public/**, no auth) and mixing the two
 * concerns into one controller class would blur that boundary.
 *
 * RBAC: same tier as RentLedgerCommandController#recordTransaction
 * (OWNER+MANAGER+STAFF) — triggering an STK push carries materially less
 * risk than recordTransaction itself: no money moves until the tenant
 * enters their own M-Pesa PIN, and the amount charged is fixed to the
 * entry's own balanceOwed rather than caller-suppliable, so a STAFF user
 * cannot use this to charge an arbitrary amount.
 */
@RestController
@RequestMapping("/api/v1/rent-ledger")
@RequiredArgsConstructor
public class RentPaymentController {

    private final RentPaymentInitiationService rentPaymentInitiationService;
    private final RentPaymentRequestRepository rentPaymentRequestRepository;

    /**
     * POST /api/v1/rent-ledger/entries/{entryId}/collect
     *
     * Triggers an STK push for the full outstanding balance of the given
     * rent ledger entry. Returns the created RentPaymentRequest (PENDING)
     * for the frontend to poll via the status endpoint below.
     */
    @PostMapping("/entries/{entryId}/collect")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ApiResponse<RentPaymentRequestResponse> collect(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID entryId,
            @Valid @RequestBody InitiateRentPaymentRequest request
    ) {
        RentPaymentRequest paymentRequest = rentPaymentInitiationService.initiate(
                requireTenantId(user),
                entryId,
                request.mpesaPhone()
        );
        return ApiResponse.ok("STK push sent. Awaiting payment.", RentPaymentRequestResponse.from(paymentRequest));
    }

    /**
     * GET /api/v1/rent-ledger/rent-payment-requests/{id}/status
     *
     * Frontend polls this after collect() — the rent-payment equivalent of
     * ReservationController's /payment-status polling endpoint.
     */
    @GetMapping("/rent-payment-requests/{id}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ApiResponse<RentPaymentRequestResponse> status(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        RentPaymentRequest paymentRequest = rentPaymentRequestRepository
                .findByIdAndTenantId(id, requireTenantId(user))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rent payment request not found",
                        ErrorCode.RESOURCE_NOT_FOUND
                ));
        return ApiResponse.ok(RentPaymentRequestResponse.from(paymentRequest));
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }
}
