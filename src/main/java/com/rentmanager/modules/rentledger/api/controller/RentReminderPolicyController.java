package com.rentmanager.modules.rentledger.api.controller;

import com.rentmanager.contract.common.ApiResponse;
import com.rentmanager.modules.rentledger.api.dto.request.UpdateRentReminderCadenceRequest;
import com.rentmanager.modules.rentledger.api.dto.response.RentReminderPolicyResponse;
import com.rentmanager.modules.rentledger.application.reminder.RentReminderPolicyService;
import com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lets a landlord see and change which rent reminders their tenants receive.
 *
 * <h2>Why this endpoint is not optional</h2>
 * Without it the cadence in {@code V85} is unreachable from the product: the
 * scheduler would start messaging real renters at 09:00 with no way to stop
 * it short of a database edit. SMS is billed to the landlord per message, so
 * a cadence they cannot see or switch off is money leaving their account on
 * our schedule.
 *
 * <h2>Authorisation</h2>
 * OWNER and MANAGER only, matching every other setting that costs the
 * landlord money. STAFF can record a payment — the caretaker case — but must
 * not be able to change what every tenant in the portfolio receives.
 *
 * <p>The tenant is taken from the verified JWT via {@code AuthenticatedUser}
 * and never from a path variable or header, so there is no id here for a
 * caller to substitute.
 */
@RestController
@RequestMapping("/api/v1/rent-reminders")
@RequiredArgsConstructor
public class RentReminderPolicyController {

    private final RentReminderPolicyService policyService;

    private UUID requireTenantId(AuthenticatedUser user) {
        UUID tenantId = user.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant associated with this user.");
        }
        return tenantId;
    }

    /**
     * The full cadence — all six milestones, with defaults filled in for any
     * the landlord has never configured.
     */
    @GetMapping("/cadence")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<List<RentReminderPolicyResponse>>> getCadence(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        UUID tenantId = requireTenantId(user);

        List<RentReminderPolicyResponse> cadence = policyService.getCadence(tenantId).stream()
                .map(RentReminderPolicyResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok("Reminder cadence retrieved", cadence));
    }

    @PutMapping("/cadence")
    @PreAuthorize("hasAnyAuthority('ROLE_LANDLORD_OWNER', 'ROLE_LANDLORD_MANAGER')")
    public ResponseEntity<ApiResponse<List<RentReminderPolicyResponse>>> updateCadence(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody UpdateRentReminderCadenceRequest request
    ) {
        UUID tenantId = requireTenantId(user);

        List<RentReminderPolicy> requested = request.milestones().stream()
                .map(m -> RentReminderPolicy.rehydrate(
                        null, tenantId, m.milestone(),
                        m.enabled(), m.smsEnabled(), m.emailEnabled(),
                        m.whatsappEnabled(), m.notifyLandlord(), null, null))
                .toList();

        List<RentReminderPolicyResponse> cadence =
                policyService.updateCadence(tenantId, requested).stream()
                        .map(RentReminderPolicyResponse::from)
                        .toList();

        return ResponseEntity.ok(ApiResponse.ok("Reminder cadence updated", cadence));
    }
}
