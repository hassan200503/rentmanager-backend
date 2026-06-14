package com.rentmanager.ai.unit;

import com.rentmanager.ai.application.AiTaskOrchestrator;
import com.rentmanager.ai.domain.port.LlmClient;
import com.rentmanager.ai.domain.service.AiBillingService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class AiTaskOrchestratorTest {

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void shouldExecuteAiTaskSuccessfully() {

        // 🔧 mocks
        LlmClient llmClient = mock(LlmClient.class);
        AiBillingService billingService = mock(AiBillingService.class);

        AiTaskOrchestrator orchestrator =
                new AiTaskOrchestrator(llmClient, billingService);

        // 🔧 stub LLM response
        when(llmClient.generate(anyString()))
                .thenReturn("{\"riskScore\":0.9}");

        // 🔥 execute (use UUID-safe input path expected by new architecture)
        String result = orchestrator.execute(TEST_TENANT_ID.toString(), "analyze risk");

        // ✅ assertion
        assertThat(result).contains("riskScore");

        // 💰 verify billing (correct UUID usage)
        verify(billingService, times(1))
                .recordUsage(eq(TEST_TENANT_ID), anyInt());
    }
}