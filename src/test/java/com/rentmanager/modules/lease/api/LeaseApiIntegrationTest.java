package com.rentmanager.modules.lease.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.infrastructure.persistence.entity.PropertyJpaEntity;
import com.rentmanager.modules.property.infrastructure.persistence.repository.PropertyJpaRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeaseApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PropertyJpaRepository propertyRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID propertyId;
    private UUID unitId;
    private UUID tenantProfileId;

    private static final String TENANT_HEADER = "X-Tenant-Id";

    @BeforeEach
    void setup() {

        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        unitId = UUID.randomUUID();
        tenantProfileId = UUID.randomUUID();

        PropertyJpaEntity property = new PropertyJpaEntity();

        // SaaS tenant isolation enforcement (correct contract usage)
        property = PropertyJpaEntity.create(tenantA);

        property.setName("Test Property");
        property.setReferenceCode("PROP-" + System.currentTimeMillis());
        property.setStatus(PropertyStatus.ACTIVE);
        property.setDescription("integration test property");

        // deterministic persistence with lifecycle callbacks
        PropertyJpaEntity saved = propertyRepository.saveAndFlush(property);

        propertyId = saved.getId();
    }

    // =========================================================
    // CREATE
    // =========================================================
    @Test
    void shouldCreateLeaseSuccessfully() throws Exception {

        String leaseId = createLease(tenantA);

        org.junit.jupiter.api.Assertions.assertFalse(leaseId.isBlank());
    }

    // =========================================================
    // FULL LIFECYCLE
    // =========================================================
    @Test
    void shouldRunFullLeaseLifecycle() throws Exception {

        String leaseId = createLease(tenantA);

        performAction(tenantA, leaseId, "APPROVE");
        performAction(tenantA, leaseId, "ACTIVATE");

        mockMvc.perform(get("/api/v1/leases/" + leaseId)
                        .header(TENANT_HEADER, tenantA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // =========================================================
    // TENANT ISOLATION
    // =========================================================
    @Test
    void shouldBlockCrossTenantAccess() throws Exception {

        String leaseId = createLease(tenantA);

        mockMvc.perform(get("/api/v1/leases/" + leaseId)
                        .header(TENANT_HEADER, tenantB.toString()))
                .andExpect(status().isForbidden());

        performActionExpectForbidden(tenantB, leaseId, "APPROVE");
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String createLease(UUID tenantId) throws Exception {

        String response = mockMvc.perform(post("/api/v1/leases")
                        .header(TENANT_HEADER, tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response)
                .path("data")
                .path("id")
                .asText();
    }

    private void performAction(UUID tenantId, String leaseId, String action) throws Exception {

        String payload = """
        {
          "performedBy": "%s",
          "action": "%s",
          "reason": "integration-test"
        }
        """.formatted(tenantId, action);

        mockMvc.perform(post("/api/v1/leases/" + leaseId + "/action")
                        .header(TENANT_HEADER, tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    private void performActionExpectForbidden(UUID tenantId, String leaseId, String action) throws Exception {

        String payload = """
        {
          "performedBy": "%s",
          "action": "%s",
          "reason": "integration-test"
        }
        """.formatted(tenantId, action);

        mockMvc.perform(post("/api/v1/leases/" + leaseId + "/action")
                        .header(TENANT_HEADER, tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isForbidden());
    }

    private String createPayload() {
        return """
        {
          "propertyId": "%s",
          "unitId": "%s",
          "tenantProfileId": "%s",
          "leaseNumber": "LS-%s",
          "leaseType": "FIXED_TERM",
          "billingCycle": "MONTHLY",
          "startDate": "2026-06-02",
          "endDate": "2026-12-01",
          "rentAmount": 20000,
          "securityDeposit": 30000,
          "lateFeeAmount": 1000,
          "gracePeriodDays": 7,
          "autoRenew": false
        }
        """.formatted(propertyId, unitId, tenantProfileId, System.currentTimeMillis());
    }
}

