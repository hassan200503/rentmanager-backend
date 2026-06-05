package com.rentmanager.modules.auth.infrastructure.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_token_user", columnList = "user_id"),
                @Index(name = "idx_refresh_token_tenant", columnList = "tenant_id"),
                @Index(name = "idx_refresh_token_value", columnList = "token", unique = true)
        }
)
@Getter
@Setter
public class RefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 1000, unique = true)
    private String token;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;
}