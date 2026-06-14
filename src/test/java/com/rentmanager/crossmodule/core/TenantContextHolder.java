package com.rentmanager.crossmodule.core;

import java.util.UUID;

public class TenantContextHolder {

    private static final ThreadLocal<UUID> TENANT = new ThreadLocal<>();

    public static void setTenantId(UUID tenantId) {
        TENANT.set(tenantId);
    }

    public static UUID getTenantId() {
        return TENANT.get();
    }

    public static void clear() {
        TENANT.remove();
    }
}