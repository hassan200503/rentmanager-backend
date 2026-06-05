package com.rentmanager.modules.auth.application.service;

import com.rentmanager.shared.security.jwt.JwtClaims;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.jwt.TokenType;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class AuthTokenService {

    private final JwtProvider jwtProvider;

    public AuthTokenService(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    public String generateAccessToken(UUID userId, UUID tenantId, String email) {

        JwtClaims claims = new JwtClaims(
                userId,
                tenantId,
                email,
                Set.of("USER"),
                TokenType.ACCESS
        );

        return jwtProvider.generateAccessToken(claims);
    }

    public String generateRefreshToken(UUID userId, UUID tenantId, String email) {

        JwtClaims claims = new JwtClaims(
                userId,
                tenantId,
                email,
                Set.of(),
                TokenType.REFRESH
        );

        return jwtProvider.generateRefreshToken(claims);
    }
}