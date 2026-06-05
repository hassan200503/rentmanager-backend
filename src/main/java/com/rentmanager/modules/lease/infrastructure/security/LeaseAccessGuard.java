package com.rentmanager.modules.lease.infrastructure.security;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Security boundary for Lease module.
 *
 * RESPONSIBILITY:
 * - Enforce tenant isolation rules
 * - Validate access to lease resources
 *
 * IMPORTANT:
 * - NO business logic
 * - ONLY authorization checks
 */
@Component
public class LeaseAccessGuard {

    /**
     * Ensures the lease belongs to the current tenant.
     */
    public void validateTenantAccess(UUID resourceTenantId, UUID currentTenantId) {
        if (resourceTenantId == null || currentTenantId == null) {
            throw new SecurityException("Tenant context missing");
        }

        if (!resourceTenantId.equals(currentTenantId)) {
            throw new SecurityException("Access denied: tenant mismatch");
        }
    }

    /**
     * Generic permission hook (extend later for roles like ADMIN, MANAGER, etc.)
     */
    public void validateOperationAllowed(String operation, String role) {
        if (operation == null || role == null) {
            throw new SecurityException("Invalid security context");
        }

        // Placeholder SaaS rule (extend later with RBAC/ABAC)
        if ("DELETE_LEASE".equals(operation) && !"ADMIN".equals(role)) {
            throw new SecurityException("Only admin can delete leases");
        }
    }
}