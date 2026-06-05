package com.rentmanager.modules.tenant.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

public class TenantResolver {

    private static final String HEADER = "X-Tenant-ID";

    public UUID resolve(HttpServletRequest request) {

        String tenantHeader = request.getHeader(HEADER);

        if (tenantHeader == null || tenantHeader.isBlank()) {
            return null;
        }

        return UUID.fromString(tenantHeader);
    }
}