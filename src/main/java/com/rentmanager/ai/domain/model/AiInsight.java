package com.rentmanager.ai.domain.model;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class AiInsight {

    private UUID id;
    private UUID tenantId;

    private AiInsightType type;
    private String title;
    private String content;

    private AiConfidence confidence;

    private UUID relatedEntityId;
    private String relatedEntityType;

    private Instant createdAt;

    protected AiInsight() {}

    public static AiInsight create(
            UUID tenantId,
            AiInsightType type,
            String title,
            String content,
            AiConfidence confidence,
            UUID relatedEntityId,
            String relatedEntityType
    ) {
        AiInsight insight = new AiInsight();
        insight.id = UUID.randomUUID();
        insight.tenantId = tenantId;
        insight.type = type;
        insight.title = title;
        insight.content = content;
        insight.confidence = confidence;
        insight.relatedEntityId = relatedEntityId;
        insight.relatedEntityType = relatedEntityType;
        insight.createdAt = Instant.now();
        return insight;
    }


}