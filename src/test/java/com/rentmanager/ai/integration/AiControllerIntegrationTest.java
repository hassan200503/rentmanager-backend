package com.rentmanager.ai.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AiControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnAiResponse() throws Exception {

        mockMvc.perform(post("/api/v1/ai/task") // ✅ FIXED PATH
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Tenant-Id", "00000000-0000-0000-0000-000000000001")
                .content("""
                {
                  "query": "risk analysis"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data").exists())
        .andExpect(jsonPath("$.data.result").exists()); // SaaS contract validation
    }
}