package com.rentmanager.modules.platformadmin.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.platformadmin.api.dto.request.AdminActivateSubscriptionRequest;
import com.rentmanager.modules.platformadmin.api.dto.request.SetLandlordCommissionRequest;
import com.rentmanager.modules.platformadmin.api.dto.request.UpdateLandlordStatusRequest;
import com.rentmanager.modules.platformadmin.api.dto.response.AdminOverviewResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordCommissionResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordDetailResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordSummaryResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.PlatformAdminInfoResponse;
import com.rentmanager.modules.platformadmin.api.dto.response.UserTypeSnapshot;
import com.rentmanager.modules.platformadmin.application.service.PlatformAdminCommissionService;
import com.rentmanager.modules.platformadmin.application.service.PlatformAdminQueryService;
import com.rentmanager.modules.tenant.application.service.SubscriptionBillingService;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.RentPaymentRequestJpaEntity;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.infrastructure.persistence.entity.DisbursementJpaEntity;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Super Admin / Platform Owner surface (read model + platform-wide config).
 *
 * Every endpoint in this module is unconditionally guarded by the
 * platform authorities derived from the Clerk {@code platformRole} claim
 * in ClerkJwtAuthenticationConverter. An endpoint here with no
 * {@code @PreAuthorize} is a bug - landlord and renter tokens must always
 * be rejected regardless of what the frontend route guard shows.
 *
 * Writes that govern money or platform policy (landlord status changes,
 * default commission, disbursement retries) require ROLE_PLATFORM_OWNER —
 * staff platform admins are read/operational only.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class PlatformAdminController {

    private static final String ROLE_PLATFORM_OWNER = "ROLE_PLATFORM_OWNER";

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("name", "slug", "email", "status", "billingMode", "createdAt", "updatedAt");

    private final PlatformAdminQueryService queryService;
    private final PlatformAdminCommissionService commissionService;
    private final SubscriptionBillingService subscriptionBillingService;

    @GetMapping("/info")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<PlatformAdminInfoResponse>> info(Authentication authentication) {
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        String platformRole = authorities.contains(ROLE_PLATFORM_OWNER) ? "OWNER" : "ADMIN";
        return ResponseEntity.ok(ApiResponse.ok(
                "Platform admin session",
                new PlatformAdminInfoResponse(platformRole)
        ));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<AdminOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.ok("Platform overview", queryService.getOverview()));
    }

    @GetMapping("/landlords")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Page<LandlordSummaryResponse>>> landlords(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100), parseSort(sort));
        return ResponseEntity.ok(ApiResponse.ok("Landlords", queryService.getLandlords(search, pageable)));
    }

    @GetMapping("/landlords/{landlordId}")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<LandlordDetailResponse>> landlordDetail(@PathVariable UUID landlordId) {
        return ResponseEntity.ok(ApiResponse.ok("Landlord detail", queryService.getLandlordDetail(landlordId)));
    }

    @GetMapping("/properties")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Page<com.rentmanager.modules.platformadmin.api.dto.response.PropertySummaryResponse>>> properties(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID landlordId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100), parseSort(sort));
        return ResponseEntity.ok(ApiResponse.ok("Properties", queryService.getProperties(search, landlordId, pageable)));
    }

    @GetMapping("/properties/{propertyId}")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<com.rentmanager.modules.platformadmin.api.dto.response.PropertyDetailResponse>> propertyDetail(
            @PathVariable UUID propertyId
    ) {
        return ResponseEntity.ok(ApiResponse.ok("Property detail", queryService.getPropertyDetail(propertyId)));
    }

    @GetMapping("/renters")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Page<com.rentmanager.modules.platformadmin.api.dto.response.RenterSummaryResponse>>> renters(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID landlordId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                Sort.unsorted());
        return ResponseEntity.ok(ApiResponse.ok("Renters", queryService.getRenters(search, landlordId, pageable)));
    }

    @GetMapping("/identity/users")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<List<UserTypeSnapshot>>> identityUsers() {
        return ResponseEntity.ok(ApiResponse.ok(
                "Identity snapshot for userType backfill",
                queryService.getUserTypes()));
    }

    @PatchMapping("/landlords/{landlordId}/status")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<Void>> updateLandlordStatus(
            @PathVariable UUID landlordId,
            @Valid @RequestBody UpdateLandlordStatusRequest request
    ) {
        queryService.updateLandlordStatus(landlordId, request.status());
        return ResponseEntity.ok(ApiResponse.ok("Landlord status updated", null));
    }

    @GetMapping("/commission/default")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<LandlordCommissionResponse>> getDefaultCommission() {
        return ResponseEntity.ok(ApiResponse.ok("Platform default commission", commissionService.getPlatformDefault()));
    }

    @PutMapping("/commission/default")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<LandlordCommissionResponse>> setDefaultCommission(
            @Valid @RequestBody SetLandlordCommissionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Platform default commission updated",
                commissionService.setPlatformDefault(request.ratePercent(), resolveActor(authentication))));
    }

    @GetMapping("/disbursements")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Page<DisbursementJpaEntity>>> disbursements(
            @RequestParam(required = false) UUID landlordId,
            @RequestParam(required = false) DisbursementStatus status,
            @RequestParam(required = false) Boolean requiresManualAttention,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok("Disbursements",
                queryService.getDisbursements(landlordId, status, requiresManualAttention, pageable)));
    }

    /**
     * The rows behind the overview's pending/failed payment counters.
     *
     * <p>Read-only, so OWNER or ADMIN — matching the disbursement queue
     * beside it. Cross-tenant by design; the role gate is the constraint.
     */
    @GetMapping("/payment-requests")
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<Page<RentPaymentRequestJpaEntity>>> paymentRequests(
            @RequestParam(required = false) UUID landlordId,
            @RequestParam(required = false) RentPaymentRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(
                page, Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.ok("Payment requests",
                queryService.getPaymentRequests(landlordId, status, pageable)));
    }

    @PostMapping("/disbursements/{disbursementId}/retry")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<Void>> retryDisbursement(@PathVariable UUID disbursementId) {
        queryService.retryDisbursement(disbursementId);
        return ResponseEntity.ok(ApiResponse.ok("Disbursement retry initiated", null));
    }

    /**
     * Manually activates a premium subscription for a landlord, bypassing
     * the M-Pesa payment gate. Used for Enterprise and other negotiated plans.
     * ROLE_PLATFORM_OWNER only — this is a money-adjacent action.
     */
    @PostMapping("/landlords/{landlordId}/subscription/activate")
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<Void>> activateLandlordSubscription(
            @PathVariable UUID landlordId,
            @Valid @RequestBody AdminActivateSubscriptionRequest request
    ) {
        subscriptionBillingService.adminActivateSubscription(
                landlordId, request.planCode(), request.periodMonths());
        return ResponseEntity.ok(ApiResponse.ok("Subscription activated", null));
    }

    private String resolveActor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getUserId() != null ? user.getUserId().toString() : user.getEmail();
        }
        return "platform-admin";
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE_FIELDS.contains(field)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}