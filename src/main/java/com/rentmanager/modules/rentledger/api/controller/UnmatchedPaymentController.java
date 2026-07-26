package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.response.UnmatchedPaymentResponse;
import com.rentmanager.modules.rentledger.application.service.UnmatchedPaymentService;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rent-ledger")
public class UnmatchedPaymentController {

    private final UnmatchedPaymentService unmatchedPaymentService;

    @GetMapping("/unmatched-payments")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER', 'ROLE_LANDLORD_STAFF')")
    public ResponseEntity<ApiResponse<List<UnmatchedPaymentResponse>>> getUnmatchedPayments(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<UnmatchedPaymentResponse> response =
                unmatchedPaymentService.getUnmatchedPayments(requireTenantId(user));
        return ResponseEntity.ok(
                ApiResponse.ok("Unmatched payments retrieved successfully", response)
        );
    }

    @PostMapping("/unmatched-payments/{transactionId}/resolve")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> resolveUnmatchedPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID transactionId,
            @RequestBody ResolveUnmatchedRequest request
    ) {
        unmatchedPaymentService.resolveUnmatchedPayment(
                requireTenantId(user),
                transactionId,
                request.unitId(),
                user.getEmail()
        );
        return ResponseEntity.ok(
                ApiResponse.ok("Unmatched payment resolved successfully", null)
        );
    }

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user. Please complete onboarding.");
        }
        return tenantId;
    }

    private record ResolveUnmatchedRequest(UUID unitId) {}
}
