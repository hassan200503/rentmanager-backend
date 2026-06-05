package com.rentmanager.modules.lease.infrastructure.security;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Facade for security operations used by application layer.
 */
@Service
public class LeaseSecurityService {

    private final LeaseAccessGuard accessGuard;

    public LeaseSecurityService(LeaseAccessGuard accessGuard) {
        this.accessGuard = accessGuard;
    }

    public void checkTenantAccess(UUID resourceTenantId, LeaseSecurityContext context) {
        accessGuard.validateTenantAccess(resourceTenantId, context.getTenantId());
    }

    public void checkDeletePermission(LeaseSecurityContext context) {
        accessGuard.validateOperationAllowed("DELETE_LEASE", context.getRole());
    }
}