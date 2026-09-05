package com.rentmanager.modules.tenant.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.notification.sms.PhoneMasker;
import com.rentmanager.modules.tenant.api.dto.request.UpdatePayoutDestinationRequest;
import com.rentmanager.modules.tenant.api.dto.response.PayoutDestinationResponse;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.exception.ErrorCode;
import com.rentmanager.shared.exception.ResourceNotFoundException;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Where a landlord's money is sent.
 *
 * <h2>Why this controller had to exist</h2>
 * {@code tenants.payout_phone_number} has been in the schema, and
 * {@code Tenant.updatePayoutPhoneNumber} has been on the aggregate, since
 * early in the project — called by nothing, reachable through no endpoint,
 * and absent from every screen. Meanwhile three code paths hard-require it:
 * the automatic B2C payout after each rent payment, the manual disbursement
 * endpoint, and the retry sweep.
 *
 * <p>The automatic path refused with a {@code log.warn} and returned. So on a
 * platform-custody deployment rent was collected, credited to the ledger, and
 * <strong>never paid out</strong> — with the only trace being a warning line
 * nobody was watching. A landlord had no way to fix it themselves because
 * there was nothing to fix it with.
 *
 * <h2>OWNER only, deliberately</h2>
 * This is the destination of every shilling the platform sends this landlord.
 * MANAGER can raise a disbursement but must not be able to change where
 * disbursements go — that separation is the whole point of deriving the
 * recipient server-side rather than accepting it per-request.
 *
 * <h2>Every change is audited</h2>
 * Account-takeover fraud in payment systems almost always begins by editing
 * the payout destination, which makes this the highest-value row in the audit
 * table. Both the previous and the new number are recorded, masked.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tenants/payout-destination")
@RequiredArgsConstructor
public class PayoutDestinationController {

    private final TenantRepository tenantRepository;
    private final FinancialAuditService financialAuditService;

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user.");
        }
        return tenantId;
    }

    /**
     * The configured destination, masked.
     *
     * <p>Never returns the full number. The landlord already knows it, and a
     * read endpoint that hands back a complete payout number turns any
     * read-only token leak into a reconnaissance step for redirecting it.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<PayoutDestinationResponse>> get(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID tenantId = requireTenantId(user);
        Tenant landlord = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Landlord not found", ErrorCode.RESOURCE_NOT_FOUND));

        String configured = landlord.getPayoutPhoneNumber();
        boolean isSet = configured != null && !configured.isBlank();

        return ResponseEntity.ok(ApiResponse.ok(
                "Payout destination retrieved",
                new PayoutDestinationResponse(isSet, isSet ? PhoneMasker.mask(configured) : null)));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROLE_LANDLORD_OWNER')")
    @Transactional
    public ResponseEntity<ApiResponse<PayoutDestinationResponse>> update(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdatePayoutDestinationRequest request
    ) {
        UUID tenantId = requireTenantId(user);
        Tenant landlord = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Landlord not found", ErrorCode.RESOURCE_NOT_FOUND));

        String previous = landlord.getPayoutPhoneNumber();
        String updated = request.payoutPhoneNumber().trim();

        landlord.updatePayoutPhoneNumber(updated);
        tenantRepository.save(landlord);

        financialAuditService.payoutDestinationChanged(
                tenantId,
                previous == null || previous.isBlank() ? "none" : PhoneMasker.mask(previous),
                PhoneMasker.mask(updated));

        // Masked: this line says a payout destination changed and for whom,
        // which is what an operator needs. The number itself is in neither
        // the log nor the audit metadata.
        log.info("Payout destination updated. tenantId={} from={} to={}",
                tenantId,
                previous == null || previous.isBlank() ? "none" : PhoneMasker.mask(previous),
                PhoneMasker.mask(updated));

        return ResponseEntity.ok(ApiResponse.ok(
                "Payout destination updated",
                new PayoutDestinationResponse(true, PhoneMasker.mask(updated))));
    }
}
