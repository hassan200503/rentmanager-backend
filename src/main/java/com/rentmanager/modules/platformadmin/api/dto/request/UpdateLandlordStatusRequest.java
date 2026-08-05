package com.rentmanager.modules.platformadmin.api.dto.request;

import com.rentmanager.modules.tenant.domain.enums.TenantStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Platform admin request to change a landlord's account status (activate, suspend).
 */
public record UpdateLandlordStatusRequest(
        @NotNull(message = "Status is required")
        TenantStatus status
) {
}
