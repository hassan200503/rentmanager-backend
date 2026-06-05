package com.rentmanager.shared.security.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "application.security")
public class SecurityProperties {

    @NotBlank(message = "JWT secret must not be blank")
    private String jwtSecret;

    @NotBlank(message = "JWT issuer must not be blank")
    private String issuer;

    /**
     * IMPORTANT:
     * Must be Duration-safe (supports YAML like "3600s", "1h", etc.)
     */
    @NotNull(message = "Access token expiration must not be null")
    private Duration accessTokenExpiration;

    @NotNull(message = "Refresh token expiration must not be null")
    private Duration refreshTokenExpiration;

    @Min(value = 4, message = "bcrypt strength must be >= 4")
    private int bcryptStrength = 12;

    private boolean enableHttpsOnlyCookies = true;

    private boolean enableSecureHeaders = true;

    // ---------------------------
    // FAIL-FAST STARTUP VALIDATION
    // ---------------------------
    @PostConstruct
    public void validate() {

        String secret = jwtSecret == null ? "" : jwtSecret.trim();

        if (secret.isBlank()) {
            throw new IllegalStateException("JWT secret is missing");
        }

        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT secret is too weak (minimum 32 characters required)"
            );
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("JWT issuer must not be blank");
        }

        if (accessTokenExpiration == null) {
            throw new IllegalStateException("Access token expiration must not be null");
        }

        if (accessTokenExpiration.isZero() || accessTokenExpiration.isNegative()) {
            throw new IllegalStateException("Access token expiration must be > 0");
        }

        if (refreshTokenExpiration == null) {
            throw new IllegalStateException("Refresh token expiration must not be null");
        }

        if (refreshTokenExpiration.isZero() || refreshTokenExpiration.isNegative()) {
            throw new IllegalStateException("Refresh token expiration must be > 0");
        }

        if (refreshTokenExpiration.compareTo(accessTokenExpiration) <= 0) {
            throw new IllegalStateException(
                    "Refresh token must be longer than access token"
            );
        }
    }

    // ---------------- getters/setters ----------------

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public void setAccessTokenExpiration(Duration accessTokenExpiration) {
        this.accessTokenExpiration = accessTokenExpiration;
    }

    public Duration getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    public void setRefreshTokenExpiration(Duration refreshTokenExpiration) {
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public int getBcryptStrength() {
        return bcryptStrength;
    }

    public void setBcryptStrength(int bcryptStrength) {
        this.bcryptStrength = bcryptStrength;
    }

    public boolean isEnableHttpsOnlyCookies() {
        return enableHttpsOnlyCookies;
    }

    public void setEnableHttpsOnlyCookies(boolean enableHttpsOnlyCookies) {
        this.enableHttpsOnlyCookies = enableHttpsOnlyCookies;
    }

    public boolean isEnableSecureHeaders() {
        return enableSecureHeaders;
    }

    public void setEnableSecureHeaders(boolean enableSecureHeaders) {
        this.enableSecureHeaders = enableSecureHeaders;
    }
}