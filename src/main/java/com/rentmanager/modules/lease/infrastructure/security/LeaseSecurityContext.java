package com.rentmanager.modules.lease.infrastructure.security;

import java.util.UUID;

/**
 * Holds security context for lease operations.
 *
 * Usually populated from:
 * - Spring Security JWT filter
 * - Request context interceptor
 */
public class LeaseSecurityContext {

    private final UUID tenantId;
    private final UUID userId;
    private final String role;

    public LeaseSecurityContext(UUID tenantId, UUID userId, String role) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}