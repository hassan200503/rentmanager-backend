package com.rentmanager.ai.application;

import com.rentmanager.ai.domain.model.AiEventSnapshot;
import org.springframework.stereotype.Component;

@Component
public class AiPromptEngine {

    public String buildPrompt(AiEventSnapshot snapshot) {

        return """
        You are an AI assistant inside a SaaS property management system.

        RULES:
        - You are tenant-isolated
        - Do not leak cross-tenant data
        - Focus only on operational insights

        EVENT:
        %s

        CONTEXT:
        %s

        TASK:
        Generate a structured insight with risk level and recommendation.
        """.formatted(
                snapshot.eventType(),
                snapshot.payload()
        );
    }
}