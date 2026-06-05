package com.rentmanager.modules.tenant.application.dto.request;

import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateTenantSubscriptionRequest {

    @NotNull(message = "Subscription status is required")
    private SubscriptionStatus subscriptionStatus;

    @Size(max = 500, message = "Change reason must not exceed 500 characters")
    private String reason;

    public UpdateTenantSubscriptionRequest() {
    }

    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }

    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}