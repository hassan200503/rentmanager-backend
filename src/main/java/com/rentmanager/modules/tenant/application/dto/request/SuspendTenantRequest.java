package com.rentmanager.modules.tenant.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SuspendTenantRequest {

    @NotBlank(message = "Suspension reason is required")
    @Size(max = 500, message = "Suspension reason must not exceed 500 characters")
    private String reason;

    public SuspendTenantRequest() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}