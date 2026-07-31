package com.rentmanager.modules.tenant.api.dto.response;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentRequestStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Returned right after a switch STK push is initiated (id + PENDING
 * status, for the frontend to poll or await callback).
 */
public record SubscriptionPaymentRequestResponse(
        UUID id,
        UUID subscriptionPlanId,
        BigDecimal amount,
        SubscriptionPaymentPurpose purpose,
        SubscriptionPaymentRequestStatus status,
        String mpesaPhone,
        String mpesaCheckoutRequestId,
        String failureReason
) {
    public static SubscriptionPaymentRequestResponse from(SubscriptionPaymentRequest request) {
        return new SubscriptionPaymentRequestResponse(
                request.getId(),
                request.getSubscriptionPlanId(),
                request.getAmount(),
                request.getPurpose(),
                request.getStatus(),
                request.getMpesaPhone(),
                request.getMpesaCheckoutRequestId(),
                request.getFailureReason()
        );
    }
}
