package com.rentmanager.modules.deposit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.deposit.api.dto.request.InitiateDepositRefundRequest;
import com.rentmanager.modules.deposit.api.dto.request.RefundDepositRequest;
import com.rentmanager.modules.deposit.api.dto.response.DepositResponse;
import com.rentmanager.modules.deposit.application.service.DepositCommandService;
import com.rentmanager.modules.deposit.domain.enums.DepositStatus;
import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.entity.TenantProfileEntity;
import com.rentmanager.modules.tenant.renter.infrastructure.persistence.repository.TenantProfileJpaRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Deposit lookups and lifecycle actions (refund, forfeit). Deposits
 * themselves are created only as a side effect of a lease's opening deposit
 * being collected — see RentLedgerApplicationService#postDeposit.
 *
 * RBAC: reads are OWNER+MANAGER+STAFF; refund/forfeit are OWNER+MANAGER only
 * (these move or permanently dispose of a tenant's money).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deposits")
public class DepositController {

    private final DepositCommandService depositCommandService;
    private final DepositRepository depositRepository;
    private final TenantProfileJpaRepository tenantProfileJpaRepository;

    /**
     * Lists deposits for the authenticated landlord.
     * Optional {@code status} filter (e.g. HELD). Without the filter, all statuses are returned.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<DepositResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) DepositStatus status
    ) {
        UUID tenantId = requireTenantId(user);
        List<Deposit> deposits = status != null
                ? depositRepository.findAllByTenantIdAndStatus(tenantId, status)
                : depositRepository.findAllByTenantId(tenantId);

        List<DepositResponse> responses = deposits.stream()
                .map(this::enrichWithRenterInfo)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok("Deposits retrieved successfully", responses));
    }

    @GetMapping("/{depositId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<DepositResponse>> getById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID depositId
    ) {
        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, requireTenantId(user))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        return ResponseEntity.ok(ApiResponse.ok("Deposit retrieved successfully",
                enrichWithRenterInfo(deposit)));
    }

    @GetMapping("/lease/{leaseId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<DepositResponse>> getByLease(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID leaseId
    ) {
        Deposit deposit = depositRepository.findByLeaseIdAndTenantId(leaseId, requireTenantId(user))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No deposit recorded for lease: " + leaseId, ErrorCode.DEPOSIT_NOT_FOUND));

        return ResponseEntity.ok(ApiResponse.ok("Deposit retrieved successfully",
                enrichWithRenterInfo(deposit)));
    }

    @PostMapping("/{depositId}/refund")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<DepositResponse>> refund(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID depositId,
            @Valid @RequestBody RefundDepositRequest request
    ) {
        Deposit deposit = depositCommandService.refundDeposit(
                requireTenantId(user), depositId,
                request.deductionAmount(), request.deductionReason(),
                request.refundReference(), request.refundRemarks()
        );

        return ResponseEntity.ok(ApiResponse.ok("Deposit refunded successfully",
                enrichWithRenterInfo(deposit)));
    }

    /**
     * Initiates a deposit refund via M-Pesa STK push to the landlord's phone.
     * The landlord enters their PIN to authorise; the callback endpoint
     * automatically completes the deposit record when Safaricom confirms.
     */
    @PostMapping("/{depositId}/refund/initiate")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<DepositResponse>> initiateRefundViaStk(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID depositId,
            @Valid @RequestBody InitiateDepositRefundRequest request
    ) {
        UUID tenantId = requireTenantId(user);
        depositCommandService.initiateRefundViaStk(
                tenantId, depositId,
                request.landlordPhone(),
                request.deductionAmount(),
                request.deductionReason(),
                request.remarks()
        );

        Deposit updated = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        return ResponseEntity.ok(ApiResponse.ok(
                "M-Pesa prompt sent — check your phone to authorise the refund",
                enrichWithRenterInfo(updated)));
    }

    /**
     * Cancels an in-flight STK push refund, allowing the landlord to retry
     * with different details or fall back to manual recording.
     */
    @PostMapping("/{depositId}/refund/cancel")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<DepositResponse>> cancelPendingRefund(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID depositId
    ) {
        UUID tenantId = requireTenantId(user);
        depositCommandService.cancelPendingRefund(tenantId, depositId);

        Deposit updated = depositRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        return ResponseEntity.ok(ApiResponse.ok("Pending refund cancelled", enrichWithRenterInfo(updated)));
    }

    @PostMapping("/{depositId}/forfeit")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<DepositResponse>> forfeit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID depositId
    ) {
        Deposit deposit = depositCommandService.forfeitDeposit(requireTenantId(user), depositId);

        return ResponseEntity.ok(ApiResponse.ok("Deposit forfeited successfully",
                enrichWithRenterInfo(deposit)));
    }

    private DepositResponse enrichWithRenterInfo(Deposit deposit) {
        String renterName = null;
        String renterPhone = null;
        if (deposit.getTenantProfileId() != null) {
            TenantProfileEntity profile = tenantProfileJpaRepository
                    .findById(deposit.getTenantProfileId()).orElse(null);
            if (profile != null) {
                renterName = profile.getFullName();
                renterPhone = profile.getPhone();
            }
        }
        return DepositResponse.fromWithRenterInfo(deposit, renterName, renterPhone);
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user.");
        }
        return tenantId;
    }
}
