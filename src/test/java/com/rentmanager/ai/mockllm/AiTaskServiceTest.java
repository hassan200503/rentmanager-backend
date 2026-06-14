package com.rentmanager.ai.mockllm;

import com.rentmanager.ai.application.AiTaskOrchestrator;
import com.rentmanager.ai.domain.port.LlmClient;
import com.rentmanager.ai.domain.service.AiBillingService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AiTaskServiceTest {

    private static final UUID TEST_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final LlmClient llmClient = mock(LlmClient.class);
    private final AiBillingService billingService = mock(AiBillingService.class);

    private final AiTaskOrchestrator service =
            new AiTaskOrchestrator(llmClient, billingService);

    @Test
    void shouldExecuteTaskThroughLlm() {

        // 🔧 mock LLM response
        when(llmClient.generate(anyString()))
                .thenReturn("{\"status\":\"success\"}");

        // 🚀 execute using SaaS UUID tenant model
        String result = service.execute(TEST_TENANT_ID.toString(), "analyze risk");

        // ✅ verify response correctness
        assertThat(result).contains("success");

        // 🔥 verify LLM was called
        verify(llmClient).generate(anyString());

        // 💰 SaaS billing verification (IMPORTANT for new architecture)
        verify(billingService, times(1))
                .recordUsage(eq(TEST_TENANT_ID), anyInt());
    }
}