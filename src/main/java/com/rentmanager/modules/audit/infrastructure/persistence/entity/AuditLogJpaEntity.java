package com.rentmanager.modules.audit.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Stands alone rather than extending {@code BaseTenantEntity}, for the same
 * reason {@code RentReminderJpaEntity} does: the base carries
 * {@code updated_at} and an {@code @Version} column, and V86 forbids updates
 * outright. Inheriting mutability machinery onto an append-only table adds
 * two columns that can never move and invites someone to try.
 *
 * <p>{@code tenantId} is nullable here, unlike on every other tenant-owned
 * entity: platform-level actions belong to no landlord organisation.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLogJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "action", nullable = false, updatable = false, length = 64)
    private String action;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private String actorId;

    @Column(name = "actor_type", nullable = false, updatable = false, length = 32)
    private String actorType;

    @Column(name = "entity_type", updatable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id", updatable = false, length = 64)
    private String entityId;

    @Column(name = "correlation_id", updatable = false)
    private String correlationId;

    @Column(name = "status", nullable = false, updatable = false, length = 16)
    private String status;

    @Column(name = "metadata", updatable = false, columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "ip_address", updatable = false, length = 64)
    private String ipAddress;

    @Column(name = "user_agent", updatable = false, length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
