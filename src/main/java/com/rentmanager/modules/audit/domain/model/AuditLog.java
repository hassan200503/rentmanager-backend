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
}