package com.rentmanager.modules.audit.domain.model;

import java.time.Instant;
import java.util.UUID;

public class AuditLog {

    private UUID id;
    private UUID tenantId;

    private String action;
    private String actorId;
    private String actorType;

    private String entityType;
    private String entityId;

    private String correlationId;

    private String status; // SUCCESS | FAILED

    private String metadata; // JSON payload

    private String ipAddress;
    private String userAgent;

    private Instant createdAt;

    public AuditLog() {}

    public AuditLog(UUID tenantId,
                    String action,
                    String actorId,
                    String actorType,
                    String entityType,
                    String entityId,
                    String correlationId,
                    String status,
                    String metadata,
                    String ipAddress,
                    String userAgent) {

        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.action = action;
        this.actorId = actorId;
        this.actorType = actorType;
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.status = status;
        this.metadata = metadata;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
    }

    /**
     * Rebuilds a persisted row. Separate from the public constructor because
     * that one stamps a fresh id and createdAt, which would silently rewrite
     * history on every read.
     */
    public static AuditLog rehydrate(
            UUID id, UUID tenantId, String action, String actorId, String actorType,
            String entityType, String entityId, String correlationId, String status,
            String metadata, String ipAddress, String userAgent, Instant createdAt) {

        AuditLog log = new AuditLog();
        log.id = id;
        log.tenantId = tenantId;
        log.action = action;
        log.actorId = actorId;
        log.actorType = actorType;
        log.entityType = entityType;
        log.entityId = entityId;
        log.correlationId = correlationId;
        log.status = status;
        log.metadata = metadata;
        log.ipAddress = ipAddress;
        log.userAgent = userAgent;
        log.createdAt = createdAt;
        return log;
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAction() { return action; }
    public String getActorId() { return actorId; }
    public String getActorType() { return actorType; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getCorrelationId() { return correlationId; }
    public String getStatus() { return status; }
    public String getMetadata() { return metadata; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
}
