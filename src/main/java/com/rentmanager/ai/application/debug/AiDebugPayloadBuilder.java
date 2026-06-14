package com.rentmanager.ai.application.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
public class AiDebugPayloadBuilder {

    private final ObjectMapper objectMapper;

    public AiDebugPayloadBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(
            String exception,
            String className,
            String method,
            String input,
            String expected,
            String actual
    ) {
        try {
            Map<String, Object> payload = Map.of(
                    "timestamp", Instant.now().toString(),
                    "exception", exception,
                    "class", className,
                    "method", method,
                    "input", input,
                    "expected", expected,
                    "actual", actual,
                    "instruction",
                    "You are debugging a Spring Boot SaaS backend. Identify root cause and give exact fix."
            );

            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);

        } catch (Exception e) {
            return "FAILED_TO_BUILD_PAYLOAD: " + e.getMessage();
        }
    }
}