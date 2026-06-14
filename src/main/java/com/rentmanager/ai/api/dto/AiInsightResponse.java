package com.rentmanager.ai.api.dto;

import com.rentmanager.ai.domain.model.AiInsight;
import com.rentmanager.ai.domain.model.AiConfidence;

import java.time.Instant;
import java.util.UUID;

public record AiInsightResponse(
        UUID id,
        String type,
        String title,
        String content,
        double confidence,
        UUID relatedEntityId,
        String relatedEntityType,
        Instant createdAt
) {

    public static AiInsightResponse from(AiInsight insight) {
        return new AiInsightResponse(
                insight.getId(),
                insight.getType().name(),
                insight.getTitle(),
                insight.getContent(),
                insight.getConfidence().score().doubleValue(),
                insight.getRelatedEntityId(),
                insight.getRelatedEntityType(),
                insight.getCreatedAt()
        );
    }
}