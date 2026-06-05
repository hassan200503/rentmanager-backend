package com.rentmanager.modules.property.infrastructure.security;

import java.util.UUID;

public final class PropertyAccessValidator {

    private PropertyAccessValidator() {
    }

    /**
     * Ensures that the property belongs to the current tenant.
     */
    public static void validateTenantAccess(UUID propertyTenantId, UUID currentTenantId) {
        if (propertyTenantId == null || !propertyTenantId.equals(currentTenantId)) {
            throw new SecurityException("Access denied: Property does not belong to tenant");
        }
    }
}