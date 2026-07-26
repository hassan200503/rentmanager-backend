package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantLeaseResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentHistoryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentReceiptResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentSummaryResponse;
import com.rentmanager.modules.rentledger.application.service.TenantPortalService;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tenant-portal")
public class TenantPortalController {

    private final TenantPortalService tenantPortalService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<TenantDashboardResponse>> getDashboard(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        TenantDashboardResponse response = tenantPortalService.getDashboard(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Dashboard retrieved successfully", response));
    }

    @GetMapping("/lease")
    public ResponseEntity<ApiResponse<TenantLeaseResponse>> getLease(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        TenantLeaseResponse response = tenantPortalService.getLease(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Lease retrieved successfully", response));
    }

    @GetMapping("/payments/summary")
    public ResponseEntity<ApiResponse<TenantPaymentSummaryResponse>> getPaymentSummary(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        TenantPaymentSummaryResponse response = tenantPortalService.getPaymentSummary(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Payment summary retrieved successfully", response));
    }

    @GetMapping("/payments/history")
    public ResponseEntity<ApiResponse<TenantPaymentHistoryResponse>> getPaymentHistory(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        TenantPaymentHistoryResponse response = tenantPortalService.getPaymentHistory(user.getUserId(), page, size);
        return ResponseEntity.ok(ApiResponse.ok("Payment history retrieved successfully", response));
    }

    @GetMapping("/payments/{transactionId}/receipt")
    public ResponseEntity<ApiResponse<TenantPaymentReceiptResponse>> getPaymentReceipt(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID transactionId
    ) {
        TenantPaymentReceiptResponse response = tenantPortalService.getPaymentReceipt(user.getUserId(), transactionId);
        return ResponseEntity.ok(ApiResponse.ok("Payment receipt retrieved successfully", response));
    }
}
