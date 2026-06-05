package com.rentmanager.modules.property.infrastructure.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class PropertySecurityContext {

    private PropertySecurityContext() {
    }

    /**
     * Gets current authenticated user ID (UUID).
     */
    public static UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            throw new SecurityException("No authenticated user found");
        }

        return UUID.fromString(authentication.getName());
    }

    /**
     * Gets current tenant ID from security context.
     * Assumes tenantId is stored as authentication detail or JWT claim mapped earlier.
     */
    public static UUID getCurrentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getDetails() == null) {
            throw new SecurityException("No tenant context found");
        }

        return UUID.fromString(authentication.getDetails().toString());
    }
}