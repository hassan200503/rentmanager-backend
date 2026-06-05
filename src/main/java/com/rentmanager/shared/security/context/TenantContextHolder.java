package com.rentmanager.shared.security.context;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT =
            new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setContext(TenantContext context) {
        CONTEXT.set(context);
    }

    public static TenantContext getContext() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}