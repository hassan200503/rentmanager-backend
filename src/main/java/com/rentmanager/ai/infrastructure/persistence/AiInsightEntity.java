package com.rentmanager.ai.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Getter
@Entity
@Table(name = "ai_insights")
public class AiInsightEntity {

    // getters only (SaaS-safe immutability style)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String response;

    @Column(nullable = false)
    private String modelUsed;

    private long latencyMs;

    private Instant createdAt;

    protected AiInsightEntity() {
        // JPA only
    }

    public AiInsightEntity(String tenantId,
                            String prompt,
                            String response,
                            String modelUsed,
                            long latencyMs,
                            Instant createdAt) {
        this.tenantId = tenantId;
        this.prompt = prompt;
        this.response = response;
        this.modelUsed = modelUsed;
        this.latencyMs = latencyMs;
        this.createdAt = createdAt;
    }

}