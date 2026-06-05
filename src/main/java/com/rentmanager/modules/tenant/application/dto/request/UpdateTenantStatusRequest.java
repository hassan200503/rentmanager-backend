package com.rentmanager.modules.tenant.application.dto.request;

import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateTenantStatusRequest {

    @NotNull(message = "Tenant status is required")
    private TenantStatus status;

    @Size(max = 500, message = "Status change reason must not exceed 500 characters")
    private String reason;

    public UpdateTenantStatusRequest() {
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void setStatus(TenantStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}