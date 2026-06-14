package com.rentmanager.ai.application.debug;

import org.springframework.stereotype.Component;

@Component
public class QwenDebugPromptBuilder {

    public String buildPrompt(String jsonPayload) {

        return """
        You are Qwen 2.5 Coder acting as a senior Spring Boot architect.

        Your job:
        1. Identify root cause (be precise)
        2. Identify wrong assumption in code
        3. Provide corrected Java code
        4. Explain minimal fix only (no theory)

        DEBUG CONTEXT:
        %s

        OUTPUT FORMAT:
        Root Cause:
        Fix:
        Corrected Code:
        """.formatted(jsonPayload);
    }
}