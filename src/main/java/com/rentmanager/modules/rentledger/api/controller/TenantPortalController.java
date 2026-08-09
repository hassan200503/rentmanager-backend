package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.announcement.api.dto.AnnouncementUnreadCountResponse;
import com.rentmanager.modules.announcement.api.dto.RenterAnnouncementResponse;
import com.rentmanager.modules.maintenance.api.dto.MaintenanceRequestResponse;
import com.rentmanager.modules.rentledger.api.autopay.dto.AutoPaySettingsResponse;
import com.rentmanager.modules.rentledger.api.dto.request.InitiatePortalPaymentRequest;
import com.rentmanager.modules.rentledger.api.dto.request.InitiateRentPaymentRequest;
import com.rentmanager.modules.rentledger.api.dto.request.SubmitMaintenanceRequest;
import com.rentmanager.modules.rentledger.api.dto.request.ToggleAutoPayRequest;
import com.rentmanager.modules.rentledger.api.dto.request.UpdateAutoPayPhoneRequest;
import com.rentmanager.modules.rentledger.api.dto.request.UpdateWhatsAppOptInRequest;
import com.rentmanager.modules.rentledger.api.dto.response.RentPaymentRequestResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantLeaseResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentHistoryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentReceiptResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantPaymentSummaryResponse;
import com.rentmanager.modules.rentledger.api.dto.response.WhatsAppOptInResponse;
import com.rentmanager.modules.rentledger.application.service.TenantPortalService;
import com.rentmanager.modules.review.api.dto.SubmitReviewRequest;
import com.rentmanager.modules.review.application.dto.response.LandlordReviewResponse;
import com.rentmanager.modules.review.application.dto.response.RenterReviewResponse;
import com.rentmanager.modules.review.application.dto.response.ReviewSummaryResponse;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
// -------------------------------------------------------
    // AUTO-PAY ENDPOINTS
    // -------------------------------------------------------

    @GetMapping("/auto-pay")
    public ResponseEntity<ApiResponse<AutoPaySettingsResponse>> getAutoPaySettings(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        AutoPaySettingsResponse response = tenantPortalService.getAutoPaySettings(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Auto-pay settings retrieved", response));
    }

    @PostMapping("/auto-pay/toggle")
    public ResponseEntity<ApiResponse<AutoPaySettingsResponse>> toggleAutoPay(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ToggleAutoPayRequest request
    ) {
        AutoPaySettingsResponse response = tenantPortalService.toggleAutoPay(
                user.getUserId(), request.enabled(), request.mpesaPhone());
        return ResponseEntity.ok(ApiResponse.ok(
                request.enabled() ? "Auto-pay enabled" : "Auto-pay disabled", response));
    }

    @PostMapping("/auto-pay/phone")
    public ResponseEntity<ApiResponse<AutoPaySettingsResponse>> updateAutoPayPhone(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateAutoPayPhoneRequest request
    ) {
        AutoPaySettingsResponse response = tenantPortalService.updateAutoPayPhone(
                user.getUserId(), request.mpesaPhone());
        return ResponseEntity.ok(ApiResponse.ok("Auto-pay phone updated", response));
    }

    // -------------------------------------------------------
    // MAINTENANCE (Phase 5) — renter-scoped
    // -------------------------------------------------------

    @PostMapping("/maintenance")
    public ResponseEntity<ApiResponse<MaintenanceRequestResponse>> submitMaintenance(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SubmitMaintenanceRequest request
    ) {
        MaintenanceRequestResponse response = tenantPortalService.submitMaintenanceRequest(
                user.getUserId(), request.title(), request.description(), request.category(), request.priority());
        return ResponseEntity.ok(ApiResponse.ok("Maintenance request submitted", response));
    }

    @GetMapping("/maintenance")
    public ResponseEntity<ApiResponse<List<MaintenanceRequestResponse>>> getMaintenanceRequests(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<MaintenanceRequestResponse> responses = tenantPortalService.getMaintenanceRequests(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Maintenance requests retrieved", responses));
    }

    // -------------------------------------------------------
    // REVIEWS (Phase 4b) — verified renter -> landlord
    // -------------------------------------------------------

    @GetMapping("/reviews/me")
    public ResponseEntity<ApiResponse<LandlordReviewResponse>> getMyReview(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        LandlordReviewResponse response = tenantPortalService.getMyReview(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(
                response != null ? "Review retrieved" : "No review yet", response));
    }

    @PostMapping("/reviews")
    public ResponseEntity<ApiResponse<LandlordReviewResponse>> submitReview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SubmitReviewRequest request
    ) {
        LandlordReviewResponse response = tenantPortalService.submitReview(
                user.getUserId(), request.rating(), request.comment());
        return ResponseEntity.ok(ApiResponse.ok("Review submitted", response));
    }

    // -------------------------------------------------------
    // RATINGS RECEIVED (V65) — landlord -> renter, approved only
    // -------------------------------------------------------

    @GetMapping("/reviews/about-me")
    public ResponseEntity<ApiResponse<List<RenterReviewResponse>>> getReviewsReceived(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<RenterReviewResponse> responses = tenantPortalService.getReviewsReceived(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Reviews retrieved", responses));
    }

    @GetMapping("/reviews/about-me/summary")
    public ResponseEntity<ApiResponse<ReviewSummaryResponse>> getReviewsReceivedSummary(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        ReviewSummaryResponse response = tenantPortalService.getReviewsReceivedSummary(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Review summary retrieved", response));
    }

    // -------------------------------------------------------
    // ANNOUNCEMENTS (broadcast messaging) — renter-scoped
    // -------------------------------------------------------

    @GetMapping("/announcements")
    public ResponseEntity<ApiResponse<List<RenterAnnouncementResponse>>> getAnnouncements(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        List<RenterAnnouncementResponse> responses = tenantPortalService.getAnnouncements(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("Announcements retrieved", responses));
    }

    @GetMapping("/announcements/unread-count")
    public ResponseEntity<ApiResponse<AnnouncementUnreadCountResponse>> getUnreadAnnouncementsCount(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        long count = tenantPortalService.getUnreadAnnouncementsCount(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(
                "Unread announcements count retrieved",
                new AnnouncementUnreadCountResponse(count)));
    }

    @PostMapping("/announcements/{id}/read")
    public ResponseEntity<ApiResponse<RenterAnnouncementResponse>> markAnnouncementRead(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        RenterAnnouncementResponse response = tenantPortalService.markAnnouncementRead(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.ok("Announcement marked as read", response));
    }

    // -------------------------------------------------------
    // WHATSAPP PREFERENCES — explicit renter consent for broadcasts
    // -------------------------------------------------------

    @GetMapping("/whatsapp-opt-in")
    public ResponseEntity<ApiResponse<WhatsAppOptInResponse>> getWhatsAppOptIn(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        WhatsAppOptInResponse response = tenantPortalService.getWhatsAppOptIn(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("WhatsApp opt-in status retrieved", response));
    }

    @PostMapping("/whatsapp-opt-in")
    public ResponseEntity<ApiResponse<WhatsAppOptInResponse>> updateWhatsAppOptIn(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateWhatsAppOptInRequest request
    ) {
        WhatsAppOptInResponse response = tenantPortalService.updateWhatsAppOptIn(user.getUserId(), request.enabled());
        return ResponseEntity.ok(ApiResponse.ok("WhatsApp opt-in updated", response));
    }
}
