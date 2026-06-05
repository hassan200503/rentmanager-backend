package com.rentmanager.shared.security.jwt;

import com.rentmanager.shared.security.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final SecurityProperties securityProperties;

    private Key signingKey;

    public JwtProvider(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @PostConstruct
    public void initialize() {
        this.signingKey = Keys.hmacShaKeyFor(
                securityProperties.getJwtSecret()
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    public String generateAccessToken(JwtClaims claims) {

        Instant now = Instant.now();

        return Jwts.builder()
                .setIssuer(securityProperties.getIssuer())
                .setSubject(claims.email())
                .claim(CLAIM_USER_ID, claims.userId().toString())
                .claim(CLAIM_TENANT_ID, claims.tenantId().toString())
                .claim(CLAIM_ROLES, claims.roles())
                .claim(CLAIM_TOKEN_TYPE, TokenType.ACCESS.name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(
                        now.plus(securityProperties.getAccessTokenExpiration())
                ))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateRefreshToken(JwtClaims claims) {

        Instant now = Instant.now();

        return Jwts.builder()
                .setIssuer(securityProperties.getIssuer())
                .setSubject(claims.email())
                .claim(CLAIM_USER_ID, claims.userId().toString())
                .claim(CLAIM_TENANT_ID, claims.tenantId().toString())
                .claim(CLAIM_TOKEN_TYPE, TokenType.REFRESH.name())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(
                        now.plus(securityProperties.getRefreshTokenExpiration())
                ))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public JwtClaims extractClaims(String token) {

        Claims claims = parseClaims(token);

        return new JwtClaims(
                UUID.fromString(claims.get(CLAIM_USER_ID, String.class)),
                UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class)),
                claims.getSubject(),
                Set.copyOf(claims.get(CLAIM_ROLES, Set.class)),
                TokenType.valueOf(claims.get(CLAIM_TOKEN_TYPE, String.class))
        );
    }

    public boolean isTokenValid(String token) {

        try {
            parseClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        Claims claims = parseClaims(token);

        return TokenType.ACCESS.name()
                .equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(String token) {
        Claims claims = parseClaims(token);

        return TokenType.REFRESH.name()
                .equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    private Claims parseClaims(String token) {

        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .requireIssuer(securityProperties.getIssuer())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}