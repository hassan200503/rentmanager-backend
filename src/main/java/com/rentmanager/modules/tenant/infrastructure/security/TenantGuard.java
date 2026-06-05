package com.rentmanager.modules.tenant.infrastructure.security;

import java.util.UUID;

public class TenantGuard {

    /**
     * Ensures tenant isolation rules are respected.
     * PURE POLICY CHECK — no state mutation allowed.
     */
    public void validateAccess(UUID requestTenantId, UUID resourceTenantId) {

        if (requestTenantId == null || resourceTenantId == null) {
            throw new SecurityException("Tenant context missing");
        }

        if (!requestTenantId.equals(resourceTenantId)) {
            throw new SecurityException("Cross-tenant access denied");
        }
    }
}