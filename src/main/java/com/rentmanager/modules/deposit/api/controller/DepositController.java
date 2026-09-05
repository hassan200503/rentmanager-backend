package com.rentmanager.modules.deposit.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.deposit.api.dto.request.RefundDepositRequest;
import com.rentmanager.modules.deposit.api.dto.response.DepositResponse;
import com.rentmanager.modules.deposit.application.service.DepositCommandService;
import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Deposit lookups and lifecycle actions (refund, forfeit). Deposits
 * themselves are created only as a side effect of a lease's opening deposit
 * being collected — see RentLedgerApplicationService#postDeposit — there is
 * deliberately no POST here to create one directly.
 *
 * RBAC mirrors RentLedgerCommandController's tiering: reads are
 * OWNER+MANAGER+STAFF (a caretaker reasonably needs to check deposit
 * status), refund/forfeit are OWNER+MANAGER only — these move or permanently
 * dispose of a tenant's money, the same tier as rent-ledger adjustments and
 * overpayment resolution.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deposits")
public class DepositController {

    private final DepositCommandService depositCommandService;
    private final DepositRepository depositRepository;

    @GetMapping("/{depositId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<DepositResponse>> getById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID depositId
    ) {
        Deposit deposit = depositRepository.findByIdAndTenantId(depositId, requireTenantId(user))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Deposit not found: " + depositId, ErrorCode.DEPOSIT_NOT_FOUND));

        return ResponseEntity.ok(ApiResponse.ok("Deposit retrieved successfully", DepositResponse.from(deposit)));
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

        return ResponseEntity.ok(ApiResponse.ok("Deposit retrieved successfully", DepositResponse.from(deposit)));
    }

    @PostMapping("/{depositId}/refund")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<DepositResponse>> refund(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID depositId,
            @Valid @RequestBody RefundDepositRequest request
    ) {
        Deposit deposit = depositCommandService.refundDeposit(
                requireTenantId(user), depositId, request.refundAmount()
        );

        return ResponseEntity.ok(ApiResponse.ok("Deposit refunded successfully", DepositResponse.from(deposit)));
    }

    @PostMapping("/{depositId}/forfeit")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<DepositResponse>> forfeit(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID depositId
    ) {
        Deposit deposit = depositCommandService.forfeitDeposit(requireTenantId(user), depositId);

        return ResponseEntity.ok(ApiResponse.ok("Deposit forfeited successfully", DepositResponse.from(deposit)));
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }
}
