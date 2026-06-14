package com.rentmanager.ai.application;

import com.rentmanager.ai.domain.model.*;
import com.rentmanager.ai.domain.repository.AiInsightRepository;
import com.rentmanager.ai.domain.service.AiBillingService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AiInsightOrchestrator {

    private final AiPromptEngine promptEngine;
    private final AiInferenceService inferenceService;
    private final AiInsightRepository repository;
    private final AiBillingService billingService;

    public AiInsightOrchestrator(
            AiPromptEngine promptEngine,
            AiInferenceService inferenceService,
            AiInsightRepository repository,
            AiBillingService billingService
    ) {
        this.promptEngine = promptEngine;
        this.inferenceService = inferenceService;
        this.repository = repository;
        this.billingService = billingService;
    }

    /**
     * 🚀 ENTRY POINT: Async SaaS-safe AI processing pipeline
     */
    @Async
    public void process(AiEventSnapshot snapshot) {

        if (!isValidSnapshot(snapshot)) {
            return;
        }

        try {
            String prompt = promptEngine.buildPrompt(snapshot);

            String response = inferenceService.infer(prompt);

            AiInsight insight = AiInsight.create(
                    snapshot.tenantId(),
                    AiInsightType.SYSTEM_ANOMALY,
                    "AI Insight",
                    response,
                    new AiConfidence(BigDecimal.valueOf(0.75)),
                    snapshot.entityId(),
                    snapshot.eventType()
            );

            repository.save(insight);

            // 💰 SaaS BILLING INTEGRATION (critical)
            billingService.recordUsage(
                    snapshot.tenantId(),
                    estimateTokens(prompt, response)
            );

        } catch (Exception ex) {
            handleFailure(snapshot, ex);
        }
    }

    /**
     * SaaS safety gate: prevents invalid pipeline execution
     */
    private boolean isValidSnapshot(AiEventSnapshot snapshot) {
        return snapshot != null
                && snapshot.tenantId() != null
                && snapshot.eventType() != null;
    }

    /**
     * Prevents null tenant propagation into billing system
     */
    private UUID safeTenant(UUID tenantId) {
        return tenantId != null ? tenantId : UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    /**
     * Simple SaaS token estimation (replace later with real tokenizer)
     */
    private int estimateTokens(String prompt, String response) {
        return (prompt.length() + response.length()) / 4;
    }

    /**
     * Failure isolation (NO SYSTEM CRASH ALLOWED)
     */
    private void handleFailure(AiEventSnapshot snapshot, Exception ex) {
        // Log only (replace with structured logging later)
        System.err.println("AI pipeline failed for tenant="
                + snapshot.tenantId()
                + " error=" + ex.getMessage());
    }
}