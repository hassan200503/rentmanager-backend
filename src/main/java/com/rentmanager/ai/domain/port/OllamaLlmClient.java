package com.rentmanager.ai.domain.port;

import com.rentmanager.ai.domain.port.LlmClient;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class OllamaLlmClient implements LlmClient {

    private final RestClient restClient;

    public OllamaLlmClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl("http://localhost:11434")
                .build();
    }

    @Override
    public String generate(String prompt) {

        Map<String, Object> request = Map.of(
                "model", "qwen2.5-coder",
                "prompt", prompt,
                "stream", false
        );

        Map response = restClient.post()
                .uri("/api/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            return "ERROR: NULL_RESPONSE";
        }

        Object result = response.get("response");

        return result != null ? result.toString() : "EMPTY_RESPONSE";
    }
}