package com.rentmanager.ai.infrastructure.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_insights")
public class AiInsightJpaEntity {

    @Id
    private UUID id;

    private UUID tenantId;

    private String type;

    @Column(length = 4000)
    private String content;

    private String title;

    private Double confidence;

    private UUID relatedEntityId;

    private String relatedEntityType;

    private Instant createdAt;
}