package com.rentmanager.modules.platformadmin.api.dto.request;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Platform admin request to change a property's status (e.g., activate, archive, flag).
 */
public record UpdatePropertyStatusRequest(
        @NotNull(message = "Status is required")
        PropertyStatus status
) {
}
