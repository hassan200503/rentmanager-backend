package com.rentmanager.shared.security.context;

import java.util.UUID;

public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER = new ThreadLocal<>();

    TenantContext() {}

    public static void setTenantId(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context not initialized");
        }
        return tenantId;
    }

    public static void setUserId(UUID userId) {
        CURRENT_USER.set(userId);
    }

    public static UUID getUserId() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
    }
}