package com.rentmanager.modules.tenant.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectTenantRequest {

    @NotBlank(message = "Rejection reason is required")
    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    private String reason;

    public RejectTenantRequest() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}