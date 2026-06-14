package com.rentmanager.ai.infrastructure.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class QwenClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public QwenClient(ObjectMapper objectMapper) {

        this.objectMapper = objectMapper;

        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:11434") // Ollama default
                .build();
    }

    public LlmResponse generate(LlmRequest request) {

        long start = System.currentTimeMillis();

        try {

            String prompt = buildPrompt(request);

            String response = restClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new OllamaRequest(
                            mapModel(request.model()),
                            prompt,
                            request.temperature(),
                            request.maxTokens()
                    ))
                    .retrieve()
                    .body(String.class);

            long latency = System.currentTimeMillis() - start;

            return LlmResponse.success(
                    response,
                    request.model().name(),
                    latency
            );

        } catch (Exception e) {
            return LlmResponse.failure(e.getMessage(), request.model().name());
        }
    }

    private String buildPrompt(LlmRequest request) {
        return """
        SYSTEM:
        %s

        USER:
        %s

        CONTEXT:
        %s
        """.formatted(
                request.systemPrompt(),
                request.userPrompt(),
                request.metadata()
        );
    }

    private String mapModel(LlmModel model) {

        return switch (model) {
            case QWEN_2_5_CODER -> "qwen2.5-coder";
            case QWEN_2_5_GENERAL -> "qwen2.5";
            default -> "qwen2.5";
        };
    }

    private record OllamaRequest(
            String model,
            String prompt,
            double temperature,
            int max_tokens
    ) {}
}