package com.rentmanager.shared.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
        String secret,
        long accessExpiration,
        long refreshExpiration
) {
}