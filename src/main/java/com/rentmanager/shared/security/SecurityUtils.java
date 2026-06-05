package com.rentmanager.shared.security;

import com.rentmanager.shared.security.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static UUID getCurrentUserId() {
        return SecurityContextHolder.get().userId();
    }

    public static UUID getCurrentTenantId() {
        return SecurityContextHolder.get().tenantId();
    }

    public static String getCurrentEmail() {
        return SecurityContextHolder.get().email();
    }
}