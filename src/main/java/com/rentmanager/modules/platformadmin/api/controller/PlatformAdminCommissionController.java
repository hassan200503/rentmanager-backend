package com.rentmanager.modules.platformadmin.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.platformadmin.api.dto.request.SetLandlordCommissionRequest;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordCommissionResponse;
import com.rentmanager.modules.platformadmin.application.service.PlatformAdminCommissionService;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Super-admin commission override endpoints for a single landlord. These are
 * intentionally separate from {@code /api/v1/commission-policies/*} (which a
 * landlord OWNER may also touch): reads are open to any platform operator,
 * but setting/clearing an override is restricted to ROLE_PLATFORM_OWNER
 * (commission is platform money), and never mutates the platform default.
 */
@RestController
@RequestMapping("/api/v1/admin/landlords/{landlordId}/commission")
@RequiredArgsConstructor
public class PlatformAdminCommissionController {

    private final PlatformAdminCommissionService commissionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_PLATFORM_OWNER', 'ROLE_PLATFORM_ADMIN')")
    public ResponseEntity<ApiResponse<LandlordCommissionResponse>> getCommission(@PathVariable UUID landlordId) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Commission status", commissionService.getCommissionFor(landlordId)));
    }

@PutMapping
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<LandlordCommissionResponse>> setCommission(
            @PathVariable UUID landlordId,
            @Valid @RequestBody SetLandlordCommissionRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                "Commission override set",
                commissionService.setCommission(landlordId, request.ratePercent(), resolveActor(authentication))));
    }

@DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    public ResponseEntity<ApiResponse<Void>> clearCommission(@PathVariable UUID landlordId) {
        commissionService.clearCommission(landlordId);
        return ResponseEntity.ok(ApiResponse.ok("Commission override cleared", null));
    }

    private String resolveActor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.getUserId() != null ? user.getUserId().toString() : user.getEmail();
        }
        return "platform-admin";
    }
}
