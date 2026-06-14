package com.rentmanager.ai.infrastructure.llm;

import java.util.Map;
import java.util.UUID;

public record LlmRequest(

        UUID tenantId,

        String systemPrompt,

        String userPrompt,

        Map<String, Object> metadata,

        LlmModel model,

        double temperature,

        int maxTokens
) {}