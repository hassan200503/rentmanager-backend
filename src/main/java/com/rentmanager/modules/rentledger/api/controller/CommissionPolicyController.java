package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.request.SetCommissionRateRequest;
import com.rentmanager.modules.rentledger.api.dto.response.CommissionPolicyResponse;
import com.rentmanager.modules.rentledger.api.dto.response.EffectiveRateResponse;
import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.rentledger.domain.model.CommissionPolicy;
import com.rentmanager.modules.rentledger.domain.repository.CommissionPolicyRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/commission-policies")
@RequiredArgsConstructor
public class CommissionPolicyController {

    private final CommissionPolicyService commissionPolicyService;
    private final CommissionPolicyRepository commissionPolicyRepository;

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user.");
        }
        return tenantId;
    }

    @GetMapping("/default")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<CommissionPolicyResponse>> getDefault(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        requireTenantId(user);
        CommissionPolicy policy = commissionPolicyRepository.findActiveDefault()
                .orElseThrow(() -> new ResourceNotFoundException("No default commission policy configured", ErrorCode.RESOURCE_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok("Default commission policy retrieved", CommissionPolicyResponse.from(policy)));
    }

    @PutMapping("/default")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER')")
    public ResponseEntity<ApiResponse<CommissionPolicyResponse>> setDefault(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SetCommissionRateRequest request
    ) {
        UUID tenantId = requireTenantId(user);
        CommissionPolicy policy = commissionPolicyService.setDefaultRate(
                request.ratePercent(),
                Instant.now(),
                tenantId.toString()
        );
        return ResponseEntity.ok(ApiResponse.ok("Default commission rate updated", CommissionPolicyResponse.from(policy)));
    }

    @GetMapping("/landlords/{landlordOrgId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<CommissionPolicyResponse>> getLandlordRate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID landlordOrgId
    ) {
        requireTenantId(user);
        CommissionPolicy policy = commissionPolicyRepository.findActiveByLandlordOrgId(landlordOrgId)
                .orElseThrow(() -> new ResourceNotFoundException("No commission policy found for landlord", ErrorCode.RESOURCE_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok("Landlord commission policy retrieved", CommissionPolicyResponse.from(policy)));
    }

    @PutMapping("/landlords/{landlordOrgId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER')")
    public ResponseEntity<ApiResponse<CommissionPolicyResponse>> setLandlordRate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID landlordOrgId,
            @Valid @RequestBody SetCommissionRateRequest request
    ) {
        UUID tenantId = requireTenantId(user);
        CommissionPolicy policy = commissionPolicyService.setLandlordRate(
                landlordOrgId,
                request.ratePercent(),
                Instant.now(),
                tenantId.toString()
        );
        return ResponseEntity.ok(ApiResponse.ok("Landlord commission rate updated", CommissionPolicyResponse.from(policy)));
    }

    @GetMapping("/effective-rate/{landlordOrgId}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<EffectiveRateResponse>> getEffectiveRate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID landlordOrgId
    ) {
        requireTenantId(user);
        java.math.BigDecimal rate = commissionPolicyService.getActiveRate(landlordOrgId);
        return ResponseEntity.ok(ApiResponse.ok("Effective rate resolved", new EffectiveRateResponse(rate)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<CommissionPolicyResponse>> getById(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        requireTenantId(user);
        CommissionPolicy policy = commissionPolicyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commission policy not found", ErrorCode.RESOURCE_NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok("Commission policy retrieved", CommissionPolicyResponse.from(policy)));
    }
}
