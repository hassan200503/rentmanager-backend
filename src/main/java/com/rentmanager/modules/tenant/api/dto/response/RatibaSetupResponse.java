package com.rentmanager.modules.tenant.api.dto.response;

import com.rentmanager.modules.tenant.domain.enums.StandingOrderStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionStandingOrder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of a Ratiba standing-order setup request: the pending order plus
 * the exact Paybill / account-reference / fee the landlord needs for the
 * manual *334# fallback, and the status of the merchant-initiated order
 * (which awaits the landlord's NI-push PIN consent).
 */
public record RatibaSetupResponse(
        UUID standingOrderId,
        StandingOrderStatus standingOrderStatus,
        String paybillNumber,
        String accountReference,
        BigDecimal amount,
        String instructions
) {
    public static RatibaSetupResponse from(
            SubscriptionStandingOrder order,
            String paybillNumber,
            String instructions
    ) {
        return new RatibaSetupResponse(
                order.getId(),
                order.getStatus(),
                paybillNumber,
                order.getAccountReference(),
                order.getAmount(),
                instructions
        );
    }
}
