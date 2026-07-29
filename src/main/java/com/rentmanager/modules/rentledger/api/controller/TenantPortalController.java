package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.request.InitiatePortalPaymentRequest;
import com.rentmanager.modules.rentledger.api.dto.request.InitiateRentPaymentRequest;
import com.rentmanager.modules.rentledger.api.dto.response.RentPaymentRequestResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantLeaseResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentHistoryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentReceiptResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentSummaryResponse;
import com.rentmanager.modules.rentledger.application.service.TenantPortalService;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
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

    @PostMapping("/entries/{entryId}/collect")
    public ResponseEntity<ApiResponse<RentPaymentRequestResponse>> collect(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID entryId,
            @Valid @RequestBody InitiateRentPaymentRequest request
    ) {
        RentPaymentRequestResponse response = tenantPortalService.initiateRentPayment(
                user.getUserId(), entryId, request.mpesaPhone());
        return ResponseEntity.ok(ApiResponse.ok("STK push sent. Awaiting payment.", response));
    }

    @PostMapping("/rent-payments/initiate")
    public ResponseEntity<ApiResponse<RentPaymentRequestResponse>> initiatePortalPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody InitiatePortalPaymentRequest request
    ) {
        RentPaymentRequestResponse response = tenantPortalService.initiatePortalPayment(
                user.getUserId(), request.amount(), request.mpesaPhone());
        return ResponseEntity.ok(ApiResponse.ok("STK push sent. Awaiting payment.", response));
    }

    @GetMapping("/rent-payment-requests/{id}/status")
    public ResponseEntity<ApiResponse<RentPaymentRequestResponse>> paymentStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        RentPaymentRequestResponse response = tenantPortalService.getPaymentRequestStatus(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Payment request status retrieved", response));
    }
}
