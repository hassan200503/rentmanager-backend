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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test for the platform-owner pricing routes. Every mutation of
 * the subscription catalog — create, reprice, deactivate — must reject every
 * non-OWNER token with 403: platform money rule, same posture as commission
 * overrides.
 *
 * <p>The reprice case updates the seeded STARTER plan with the values it
 * already holds, making the write a no-op on the shared catalog. Create and
 * deactivate assert only refusals, which never reach the service and so
 * leave the catalog untouched — see the comment above those tests.
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

    // ── Creating and withdrawing plans, same gate ────────────────────────
    //
    // These two carried no @PreAuthorize at all while the reprice route
    // above did, so the security chain's closing anyRequest().authenticated()
    // was the only thing in front of them: any signed-in user, a renter
    // included, could add a priced plan to the catalog the public pricing
    // page serves, or deactivate the live plans for every landlord at once.
    // Subscription plans are global rows, so unlike most of this codebase
    // there is no TenantContext scoping underneath to limit the blast radius.
    //
    // Only the refusals are asserted. A successful create would add a row to
    // the shared catalog, and a successful deactivate would switch off the
    // seeded STARTER plan that other tests read — whereas a refusal is
    // rejected before the service is reached, so it leaves nothing behind.

    @Test
    void renterToken_cannotCreatePlan() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/subscription-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordOwnerToken_cannotCreatePlan() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/subscription-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdmin_cannotCreatePlan() throws Exception {
        mockMvc.perform(post("/api/v1/tenants/subscription-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void renterToken_cannotDeactivatePlan() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/subscription-plans/{id}/deactivate", STARTER_PLAN_ID)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordOwnerToken_cannotDeactivatePlan() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/subscription-plans/{id}/deactivate", STARTER_PLAN_ID)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequest_cannotDeactivatePlan() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/subscription-plans/{id}/deactivate", STARTER_PLAN_ID))
                .andExpect(status().isForbidden());
    }
}