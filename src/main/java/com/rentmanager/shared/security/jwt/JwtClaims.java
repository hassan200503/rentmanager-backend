package com.rentmanager.shared.security.jwt;

import java.util.Set;
import java.util.UUID;

public record JwtClaims(

        UUID userId,
        UUID tenantId,
        String email,
        Set<String> roles,
        TokenType tokenType
) {
}