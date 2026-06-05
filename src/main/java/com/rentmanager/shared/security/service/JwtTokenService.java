package com.rentmanager.shared.security.service;

import com.rentmanager.shared.security.jwt.JwtClaims;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.jwt.TokenType;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtProvider jwtProvider;

    public JwtTokenService(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    public String generateAccessToken(UUID userId, UUID tenantId, String email, Set<String> roles) {

        JwtClaims claims = new JwtClaims(
                userId,
                tenantId,
                email,
                roles,
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