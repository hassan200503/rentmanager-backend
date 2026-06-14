package com.rentmanager.modules.tenant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.RentManagerApplication;
import com.rentmanager.modules.tenant.application.command.service.TenantCommandService;
import com.rentmanager.modules.tenant.application.dto.request.CreateTenantRequest;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RentManagerApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TenantApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantCommandService tenantCommandService;

    @Test
    void should_create_tenant_via_api() throws Exception {

        // =========================================================
        // CONTEXT (simulated tenant from X-Tenant-Id or JWT)
        // =========================================================
        UUID tenantId = UUID.randomUUID();

        // =========================================================
        // REQUEST DTO (FULL ALIGNMENT WITH REAL CLASS)
        // =========================================================
        CreateTenantRequest request = new CreateTenantRequest();

        // NOTE: Lombok @Getter only → so we must rely on constructor-less setters via reflection style JSON
        // Jackson will populate fields during request serialization

        // easiest safe approach: use ObjectMapper JSON construction instead of setters
        String requestJson = """
        {
          "tenantCode": "T-100",
          "name": "API Tenant",
          "slug": "api-tenant",
          "email": "api@tenant.com",
          "phoneNumber": "0800000000",
          "tenantType": "STANDARD",
          "subscriptionStatus": "ACTIVE"
        }
        """;

        // =========================================================
        // RESPONSE DTO
        // =========================================================
        TenantResponse response = new TenantResponse();
        response.setTenantId(UUID.randomUUID());
        response.setName("API Tenant");
        response.setEmail("api@tenant.com");
        response.setPhoneNumber("0800000000");
        response.setAddress("N/A");
        response.setStatus("ACTIVE");

        // =========================================================
        // MOCK SERVICE (CORRECT CONTRACT)
        // =========================================================
        when(tenantCommandService.createTenant(
                eq(tenantId),
                any(CreateTenantRequest.class)
        )).thenReturn(response);

        // =========================================================
        // EXECUTION
        // =========================================================
        mockMvc.perform(post("/api/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", tenantId.toString())
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").exists())
                .andExpect(jsonPath("$.data.name").value("API Tenant"))
                .andExpect(jsonPath("$.data.email").value("api@tenant.com"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0800000000"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}