package com.rentmanager.modules.tenant.api;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.support.MockTenantAuthentication;
import com.rentmanager.modules.support.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test for the platform-owner pricing route: repricing a
 * subscription plan (PUT /api/v1/tenants/subscription-plans/{id}) must
 * reject every non-OWNER token with 403 — platform money rule, same posture
 * as commission overrides. The seeded STARTER plan is updated with the
 * values it already holds, making the write a no-op on the shared catalog.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
class SubscriptionPlanUpdateRbacTest {

    private static final UUID STARTER_PLAN_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID TENANT_ID = UUID.randomUUID();

    private static final String BODY = """
            {
              "code": "STARTER",
              "name": "Starter",
              "description": "Up to 10 units",
              "billingCycle": "MONTHLY",
              "maxProperties": null,
              "maxUnits": 10,
              "maxUsers": null,
              "maxStorageGb": null,
              "monthlyPrice": 2500.00,
              "yearlyPrice": null
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void platformOwner_canRepricePlan() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/subscription-plans/{id}", STARTER_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk());
    }

    @Test
    void platformAdmin_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/subscription-plans/{id}", STARTER_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordOwnerToken_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/subscription-plans/{id}", STARTER_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void renterToken_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/subscription-plans/{id}", STARTER_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequest_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/subscription-plans/{id}", STARTER_PLAN_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
    }
}