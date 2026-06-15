package com.rentmanager.modules.lease.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@Rollback
@SpringBootTest
@AutoConfigureMockMvc
public class LeaseApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private static final UUID TENANT_A =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID TENANT_B =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private String createLease(UUID tenantId) throws Exception {

        String payload = """
        {
          "propertyId": "33333333-3333-3333-3333-333333333333",
          "unitId": "44444444-4444-4444-4444-444444444444",
          "tenantProfileId": "55555555-5555-5555-5555-555555555555",
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
        """.formatted(System.currentTimeMillis());

        MvcResult result = mockMvc.perform(post("/api/v1/leases")
                        .header(TENANT_HEADER, tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.id"
        );
    }

    private void performAction(String leaseId, String action) throws Exception {

        String payload = """
        {
          "performedBy": "%s",
          "action": "%s",
          "reason": "integration-test"
        }
        """.formatted(UUID.randomUUID(), action);

        mockMvc.perform(post("/api/v1/leases/%s/action".formatted(leaseId))
                        .header(TENANT_HEADER, TENANT_A.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCreateLeaseSuccessfully() throws Exception {

        String leaseId = createLease(TENANT_A);

        assertFalse(leaseId.isBlank());
    }

    @Test
    void shouldBlockCrossTenantAccess() throws Exception {

        String leaseId = createLease(TENANT_A);

        mockMvc.perform(get("/api/v1/leases/" + leaseId)
                        .header(TENANT_HEADER, TENANT_B.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRunFullLeaseLifecycle() throws Exception {

        String leaseId = createLease(TENANT_A);

        performAction(leaseId, "APPROVE");
        performAction(leaseId, "ACTIVATE");

        mockMvc.perform(get("/api/v1/leases/" + leaseId)
                        .header(TENANT_HEADER, TENANT_A.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}