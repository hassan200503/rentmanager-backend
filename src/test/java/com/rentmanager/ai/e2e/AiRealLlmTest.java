package com.rentmanager.ai.e2e;

import com.rentmanager.ai.domain.port.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
class AiRealLlmTest {

    @Autowired
    private LlmClient ollamaClient;

    @Test
    void shouldCallRealModel() {

        String response = ollamaClient.generate(
                "Analyze tenant payment risk"
        );

        assertThat(response).isNotBlank();
        assertThat(response.toLowerCase()).containsAnyOf(
                "risk", "payment", "tenant"
        );
    }
}