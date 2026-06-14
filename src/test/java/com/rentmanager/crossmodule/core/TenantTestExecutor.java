package com.rentmanager.crossmodule.core;

import com.rentmanager.crossmodule.core.TenantContextHolder;

import java.util.UUID;

public class TenantTestExecutor {

    private final ScenarioContext context;

    private static final String TENANT_KEY = "CURRENT_TENANT";

    public TenantTestExecutor(ScenarioContext context) {
        this.context = context;
    }

    public <T> T executeAsTenant(UUID tenantId, java.util.function.Supplier<T> action) {
        UUID previousTenant = context.get(TENANT_KEY);

        try {
            context.put(TENANT_KEY, tenantId);

            TenantContextHolder.setTenantId(tenantId);

            return action.get();

        } finally {
            TenantContextHolder.clear();

            if (previousTenant != null) {
                context.put(TENANT_KEY, previousTenant);
                TenantContextHolder.setTenantId(previousTenant);
            } else {
                context.clear();
            }
        }
    }

    public void executeAsTenant(UUID tenantId, Runnable action) {
        executeAsTenant(tenantId, () -> {
            action.run();
            return null;
        });
    }
}