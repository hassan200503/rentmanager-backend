package com.rentmanager.ai.application;

import com.rentmanager.ai.domain.port.LlmClient;
import com.rentmanager.ai.domain.service.AiBillingService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiTaskOrchestrator {

    private final LlmClient llmClient;
    private final AiBillingService billingService;

    public AiTaskOrchestrator(LlmClient llmClient,
                              AiBillingService billingService) {
        this.llmClient = llmClient;
        this.billingService = billingService;
    }

    public String execute(String tenantId, String query) {

        String prompt = buildPrompt(tenantId, query);

        String response = llmClient.generate(prompt);

        billingService.recordUsage(UUID.fromString(tenantId), response.length());

        return response;
    }

    private String buildPrompt(String tenantId, String query) {
        return "Tenant=" + tenantId + "\nQuery=" + query;
    }





}