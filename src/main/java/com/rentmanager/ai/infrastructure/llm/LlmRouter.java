package com.rentmanager.ai.infrastructure.llm;

import org.springframework.stereotype.Component;

@Component
public class LlmRouter {

    public LlmModel route(String taskType, String contextType) {

        return switch (taskType) {

            case "LEASE_ANALYSIS" -> LlmModel.QWEN_2_5_CODER;
            case "PROPERTY_INSIGHT" -> LlmModel.QWEN_2_5_GENERAL;
            case "RISK_DETECTION" -> LlmModel.QWEN_2_5_CODER;

            default -> LlmModel.QWEN_2_5_GENERAL;
        };
    }
}