package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.request.InitiateB2CDisbursementRequest;
import com.rentmanager.modules.rentledger.application.service.B2CDisbursementService;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disbursements")
@RequiredArgsConstructor
public class DisbursementController {

    private final B2CDisbursementService b2cDisbursementService;
    private final DisbursementRepository disbursementRepository;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<List<DisbursementResponse>>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status
    ) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    "No tenant associated with your account", "TENANT_REQUIRED"));
        }

        List<Disbursement> disbursements;
        if (status != null && !status.isEmpty()) {
            DisbursementStatus ds = DisbursementStatus.valueOf(status.toUpperCase());
            disbursements = disbursementRepository.findByTenantIdAndStatusIn(tenantId, List.of(ds));
        } else {
            disbursements = disbursementRepository.findByTenantId(tenantId);
        }

        List<DisbursementResponse> responses = disbursements.stream()
                .map(DisbursementResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok("Disbursements retrieved", responses));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<DisbursementResponse>> initiate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody InitiateB2CDisbursementRequest request
    ) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    "No tenant associated with your account", "TENANT_REQUIRED"));
        }

        Disbursement disbursement = b2cDisbursementService.initiateDisbursement(
                tenantId,
                request.leaseId(),
                request.ledgerEntryId(),
                request.amount(),
                request.recipientPhone(),
                request.recipientName(),
                request.commandId() != null ? request.commandId() : "BusinessPayment",
                request.remarks() != null ? request.remarks() : "Disbursement"
        );

        return ResponseEntity.ok(ApiResponse.ok(
                "Disbursement initiated",
                DisbursementResponse.from(disbursement)
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<DisbursementResponse>> getStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id
    ) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.fail(
                    "No tenant associated with your account", "TENANT_REQUIRED"));
        }
        Disbursement disbursement = disbursementRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new com.rentmanager.shared.exception.ResourceNotFoundException(
                        "Disbursement not found",
                        com.rentmanager.shared.exception.ErrorCode.RESOURCE_NOT_FOUND
                ));
        return ResponseEntity.ok(ApiResponse.ok("Disbursement status retrieved",
                DisbursementResponse.from(disbursement)));
    }

    public record DisbursementResponse(
            UUID id,
            UUID leaseId,
            UUID ledgerEntryId,
            java.math.BigDecimal amount,
            String recipientPhone,
            String recipientName,
            String commandId,
            String status,
            String mpesaTransactionId,
            String failureReason,
            java.time.Instant createdAt
    ) {
        static DisbursementResponse from(Disbursement d) {
            return new DisbursementResponse(
                    d.getId(), d.getLeaseId(), d.getLedgerEntryId(),
                    d.getAmount(), d.getRecipientPhone(), d.getRecipientName(),
                    d.getCommandId(), d.getStatus().name(),
                    d.getMpesaTransactionId(), d.getFailureReason(),
                    d.getCreatedAt()
            );
        }
    }
}