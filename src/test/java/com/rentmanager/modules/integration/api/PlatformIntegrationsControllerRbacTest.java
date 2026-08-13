package com.rentmanager.modules.integration.api;

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
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test for the Integrations control plane: every
 * {@code /api/v1/admin/integrations/**} read is open to OWNER/ADMIN only,
 * every write (save / activate / test) is OWNER-only — landlord, renter and
 * unauthenticated tokens are refused with 403 regardless of frontend route
 * guards. Mirrors {@code PlatformAdminControllerRbacTest}'s harness.
 *
 * {@code @Transactional}: every write rolls back, so the shared test DB never
 * sees a {@code daraja/DEVELOPMENT} config row — such a row would short-circuit
 * the environment credential fallback that other integration tests rely on
 * (see {@code PlatformDarajaCredentialsResolver}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
@Transactional
class PlatformIntegrationsControllerRbacTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    // ---------- reads: OWNER + ADMIN ----------

    @Test
    void platformOwner_canListIntegrations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/integrations")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6));
    }

    @Test
    void platformAdmin_canListIntegrations() throws Exception {
        mockMvc.perform(get("/api/v1/admin/integrations")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void platformOwner_canReadProviderDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/integrations/daraja")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerKey").value("daraja"));
    }

    @Test
    void platformAdmin_canReadAuditTrail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/integrations/daraja/audit")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_ADMIN")))
                .andExpect(status().isOk());
    }

    // ---------- reads: everyone else forbidden ----------

    @Test
    void landlordToken_isForbiddenOnList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/integrations")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void renterToken_isForbiddenOnProviderDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/integrations/daraja")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_RENTER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequest_isForbiddenOnAudit() throws Exception {
        mockMvc.perform(get("/api/v1/admin/integrations/daraja/audit"))
                .andExpect(status().isForbidden());
    }

    // ---------- writes: OWNER only ----------

    @Test
    void platformOwner_canSaveCredentials() throws Exception {
        mockMvc.perform(put("/api/v1/admin/integrations/daraja/DEVELOPMENT")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentials\":{\"consumer_key\":\"ck\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerKey").value("daraja"));
    }

    @Test
    void platformAdmin_isForbiddenOnSaveCredentials() throws Exception {
        mockMvc.perform(put("/api/v1/admin/integrations/daraja/DEVELOPMENT")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentials\":{\"consumer_key\":\"ck\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordToken_isForbiddenOnSaveCredentials() throws Exception {
        mockMvc.perform(put("/api/v1/admin/integrations/daraja/DEVELOPMENT")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentials\":{\"consumer_key\":\"ck\"}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformOwner_activateReachesService() throws Exception {
        // Save first so activation succeeds — the point is that the OWNER
        // token passed the authority check and reached the control plane.
        mockMvc.perform(put("/api/v1/admin/integrations/daraja/DEVELOPMENT")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"credentials\":{\"consumer_key\":\"ck\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/integrations/daraja/DEVELOPMENT/activate")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.environment.active").value(true));
    }

    @Test
    void platformAdmin_isForbiddenOnActivate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/integrations/daraja/DEVELOPMENT/activate")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformOwner_canRunTestConnection() throws Exception {
        // No credentials in the test database: the real tester fails fast
        // with a provider message — the endpoint still answers 200 with the
        // recorded outcome.
        // Note: data.ok depends on the machine's Daraja credentials (real
        // sandbox creds fall through from the environment), so only the
        // OWNER's ability to invoke the control plane is asserted here.
        mockMvc.perform(post("/api/v1/admin/integrations/daraja/DEVELOPMENT/test")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk());
    }

    @Test
    void landlordToken_isForbiddenOnTestConnection() throws Exception {
        mockMvc.perform(post("/api/v1/admin/integrations/daraja/DEVELOPMENT/test")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }
}
