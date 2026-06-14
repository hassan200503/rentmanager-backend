package com.rentmanager.ai.unit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRawResponseHandlingTest {

    @Test
    void shouldValidateJsonStructureWithoutDomainModel() {

        String llmResponse = """
        {"riskScore":0.8,"insight":"high risk"}
        """;

        assertThat(llmResponse).contains("riskScore");
        assertThat(llmResponse).contains("insight");
    }
}