package com.rentmanager.shared.security.principal;

import java.util.Set;
import java.util.UUID;

public record AuthPrincipal(
        UUID userId,
        UUID tenantId,
        String email,
        Set<String> roles
) {
}