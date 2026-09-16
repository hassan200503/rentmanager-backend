package com.rentmanager.modules.platformadmin.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.platformadmin.api.dto.request.SetLandlordCommissionRequest;
import com.rentmanager.modules.platformadmin.api.dto.response.LandlordCommissionResponse;
import com.rentmanager.modules.platformadmin.application.service.PlatformAdminCommissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Super-admin commission read endpoints for a single landlord.
 *
 * <p>Write (PUT/DELETE) operations are no longer supported: RentManager's
 * revenue model is subscription-only as of V89. All rent payments settle
 * directly into the landlord's own M-Pesa (DIRECT collection mode), so no
 * commission is ever deducted from rent. GET is retained for audit/migration
 * diagnostics only.
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
    @Deprecated(since = "2.0")
    public ResponseEntity<ApiResponse<Void>> setCommission(
            @PathVariable UUID landlordId,
            @Valid @RequestBody SetLandlordCommissionRequest request
    ) {
        // Commission rates are not applied under DIRECT collection mode (V89).
        // Platform revenue is subscription-only. This endpoint is retired.
        return ResponseEntity.status(410).body(ApiResponse.fail(
                "Commission overrides are no longer supported. "
                + "RentManager uses subscription-only billing. "
                + "Use POST /api/v1/admin/landlords/{id}/subscription/activate to assign a plan.",
                "COMMISSION_MODEL_RETIRED"));
    }

@DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_PLATFORM_OWNER')")
    @Deprecated(since = "2.0")
    public ResponseEntity<ApiResponse<Void>> clearCommission(@PathVariable UUID landlordId) {
        // Commission rates are not applied under DIRECT collection mode (V89).
        // This endpoint is retired.
        return ResponseEntity.status(410).body(ApiResponse.fail(
                "Commission overrides are no longer supported. "
                + "RentManager uses subscription-only billing.",
                "COMMISSION_MODEL_RETIRED"));
    }

}
