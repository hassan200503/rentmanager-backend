package com.rentmanager.modules.activity.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "activity_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ActivityLog {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "metadata")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static ActivityLog record(
            UUID tenantId,
            String eventType,
            String entityType,
            UUID entityId,
            String entityName,
            UUID actorId,
            String actorName,
            Map<String, Object> metadata
    ) {
        return ActivityLog.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .eventType(eventType)
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .actorId(actorId)
                .actorName(actorName)
                .metadata(metadata)
                .createdAt(Instant.now())
                .build();
    }
}