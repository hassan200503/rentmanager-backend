package com.rentmanager.modules.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final UUID tenantId;

    private final String token;
    private final Instant expiresAt;

    private boolean revoked;

    public RefreshToken(
            UUID id,
            UUID userId,
            UUID tenantId,
            String token,
            Instant expiresAt
    ) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    public boolean isValid() {
        return !revoked && Instant.now().isBefore(expiresAt);
    }

    public void revoke() {
        this.revoked = true;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTenantId() { return tenantId; }
    public String getToken() { return token; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
}