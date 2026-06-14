package com.rentmanager.ai.application;

import com.rentmanager.ai.infrastructure.llm.LlmRequest;
import com.rentmanager.ai.infrastructure.llm.LlmResponse;
import com.rentmanager.ai.infrastructure.llm.QwenClient;
import org.springframework.stereotype.Service;

@Service
public class AiInferenceService {

    private final QwenClient qwenClient;

    public AiInferenceService(QwenClient qwenClient) {
        this.qwenClient = qwenClient;
    }




    public String infer(String prompt) {

        LlmRequest request = new LlmRequest(
                java.util.UUID.randomUUID(),
                "default-tenant",
                prompt,
                java.util.Map.of(
                        "systemPrompt", "SYSTEM: You are a SaaS AI assistant"
                ),
                com.rentmanager.ai.infrastructure.llm.LlmModel.QWEN_2_5_CODER,
                0.7,
                2048
        );

        LlmResponse response = qwenClient.generate(request);

        return response.content();
    }

}