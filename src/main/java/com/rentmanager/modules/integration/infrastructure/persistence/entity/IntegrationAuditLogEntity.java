package com.rentmanager.modules.integration.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail for integration actions (created / updated /
 * activated / deactivated / tested / revealed). Metadata carries redacted,
 * field-level diffs only - secret values are never persisted here.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(
        name = "integration_audit_log",
        indexes = @Index(name = "idx_integration_audit_provider", columnList = "provider_key, created_at")
)
public class IntegrationAuditLogEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_key", nullable = false, length = 50)
    private String providerKey;

    @Column(name = "environment", nullable = false, length = 20)
    private String environment;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Column(name = "actor_user_id", nullable = false, length = 200)
    private String actorUserId;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public IntegrationAuditLogEntity(
            String providerKey,
            String environment,
            String action,
            String actorUserId,
            String metadata,
            String ipAddress
    ) {
        this.id = UUID.randomUUID();
        this.providerKey = providerKey;
        this.environment = environment;
        this.action = action;
        this.actorUserId = actorUserId;
        this.metadata = metadata;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }
}