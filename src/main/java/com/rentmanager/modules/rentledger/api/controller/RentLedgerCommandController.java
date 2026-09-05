package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.request.ApplyAdjustmentRequest;
import com.rentmanager.modules.rentledger.api.dto.request.RecordRentTransactionRequest;
import com.rentmanager.modules.rentledger.api.dto.request.ResolveOverpaymentCreditRequest;
import com.rentmanager.modules.rentledger.api.dto.request.ResolveOverpaymentRefundRequest;
import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerEntryResponse;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Command-side endpoints for the rent ledger. Deliberately does NOT expose
 * postCharge or markOverdue — those are scheduler/orchestrator-only entry
 * points, not meant to be directly callable over HTTP.
 *
 * FIX (earlier session): recordedBy is now derived from
 * AuthenticatedUser.getEmail() rather than the request body. Previously a
 * client could supply any string as recordedBy, meaning a caller could
 * record a payment/adjustment/refund as having been recorded by anyone —
 * a real audit-trail integrity gap on a controller that moves money.
 * getEmail() was chosen over getUserId() as the stored value because it's
 * the human-readable identity an admin reviewing the ledger would
 * recognize, matching AuthenticatedUser's own getUsername() contract
 * (which also returns email). If a stable non-PII identifier is preferred
 * instead, getUserId() is available on AuthenticatedUser as the
 * alternative — not used here without an explicit decision to do so.
 *
 * RBAC (added this session, per Addendum 2 §4.1 resolution):
 * recordTransaction is the ordinary path for logging a rent payment --
 * OWNER+MANAGER+STAFF. This is deliberately the same operational tier as
 * Unit's markOccupied/markVacant: the person physically collecting a
 * caretaker's cash or M-Pesa receipt is very often STAFF, not a MANAGER
 * or the OWNER, and requiring MANAGER+ here would create a real
 * operational bottleneck around every rent collection.
 *
 * applyAdjustment / resolveOverpaymentWithRefund / resolveOverpaymentAsCredit
 * are gated OWNER+MANAGER, STAFF excluded. These are the actions that can
 * quietly alter a balance or move money back out -- the same
 * fraud-resistance reasoning already locked into the domain design
 * (OVERPAID as a held state requiring explicit admin resolution, no
 * auto-carry-forward) extends naturally to who is authorized to perform
 * that resolution.
 */
@RestController
@RequestMapping("/api/v1/rent-ledger")
@RequiredArgsConstructor
public class RentLedgerCommandController {

    private final RentLedgerApplicationService rentLedgerApplicationService;

    @PostMapping("/entries/{entryId}/transactions")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ApiResponse<RentLedgerEntryResponse> recordTransaction(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID entryId,
            @Valid @RequestBody RecordRentTransactionRequest request
    ) {
        RentLedgerEntry entry = rentLedgerApplicationService.applyTransaction(
                requireTenantId(user),
                UUID.randomUUID().toString(),
                entryId,
                request.type(),
                request.amount(),
                request.externalReference(),
                request.source(),
                user.getEmail(),
                request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now()
        );
        return ApiResponse.ok(RentLedgerEntryResponse.from(entry));
    }

    /**
     * Despite the DELETE verb (kept for API compatibility with existing
     * clients), this does NOT hard-delete the transaction — it posts a
     * compensating REVERSAL transaction and keeps both rows. See
     * RentLedgerApplicationService#reverseTransaction.
     */
    @DeleteMapping("/transactions/{transactionId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<Void> deleteTransaction(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID transactionId
    ) {
        rentLedgerApplicationService.reverseTransaction(
                requireTenantId(user),
                transactionId,
                user.getEmail()
        );
        return ApiResponse.ok("Transaction reversed", null);
    }

    @PostMapping("/entries/{entryId}/adjustments")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<RentLedgerEntryResponse> applyAdjustment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID entryId,
            @Valid @RequestBody ApplyAdjustmentRequest request
    ) {
        RentLedgerEntry entry = rentLedgerApplicationService.applyAdjustment(
                requireTenantId(user),
                UUID.randomUUID().toString(),
                entryId,
                request.delta(),
                user.getEmail(),
                request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now()
        );
        return ApiResponse.ok(RentLedgerEntryResponse.from(entry));
    }

    @PostMapping("/entries/{entryId}/overpayment/refund")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<RentLedgerEntryResponse> resolveOverpaymentWithRefund(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID entryId,
            @Valid @RequestBody ResolveOverpaymentRefundRequest request
    ) {
        RentLedgerEntry entry = rentLedgerApplicationService.resolveOverpaymentWithRefund(
                requireTenantId(user),
                entryId,
                request.refundAmount(),
                request.externalReference(),
                request.source(),
                user.getEmail(),
                request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now()
        );
        return ApiResponse.ok(RentLedgerEntryResponse.from(entry));
    }

    @PostMapping("/entries/{sourceEntryId}/overpayment/credit")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ApiResponse<Void> resolveOverpaymentAsCredit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID sourceEntryId,
            @Valid @RequestBody ResolveOverpaymentCreditRequest request
    ) {
        rentLedgerApplicationService.resolveOverpaymentAsCredit(
                requireTenantId(user),
                UUID.randomUUID().toString(),
                sourceEntryId,
                request.targetLedgerEntryId(),
                user.getEmail(),
                request.occurredAt() != null ? request.occurredAt() : LocalDateTime.now()
        );
        return ApiResponse.ok("Overpayment applied as credit", null);
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }
}