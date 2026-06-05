package com.rentmanager.shared.security.context;

import java.util.UUID;

public class SecurityContextHolder {

    private static final ThreadLocal<SecurityContext> CONTEXT = new ThreadLocal<>();

    public static void set(SecurityContext context) {
        CONTEXT.set(context);
    }

    public static SecurityContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public record SecurityContext(
            UUID userId,
            UUID tenantId,
            String email
    ) {}
}