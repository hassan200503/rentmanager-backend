package com.rentmanager.modules.platformadmin.api;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Acceptance test for the Super Admin surface: every {@code /api/v1/admin/**}
 * endpoint must reject landlord, renter, and unauthenticated tokens with 403,
 * independent of any frontend route guard. Platform owner/admin tokens pass.
 *
 * Mirrors {@code DisbursementControllerRbacTest}'s harness:
 * {@code @SpringBootTest(RANDOM_PORT)} + {@code @Import(TestSecurityConfig)}
 * (chain permits all so method-level {@code @PreAuthorize} is what's under
 * test) + {@code MockTenantAuthentication} to inject authorities. The full
 * Spring context is loaded, so these hit the real (empty) test database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
class PlatformAdminControllerRbacTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID UNKNOWN_LANDLORD_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    // ---------- info ----------

    @Test
    void platformOwner_canAccessAdminInfo() throws Exception {
        mockMvc.perform(get("/api/v1/admin/info")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformRole").value("OWNER"));
    }

    @Test
    void platformAdmin_canAccessAdminInfo() throws Exception {
        mockMvc.perform(get("/api/v1/admin/info")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformRole").value("ADMIN"));
    }

    @Test
    void landlordOwnerToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/info")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordManagerToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/info")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordStaffToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/info")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void renterToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/info")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingOnboardingToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/info")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PENDING_ONBOARDING")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequest_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/info"))
                .andExpect(status().isForbidden());
    }

    // ---------- overview ----------

    @Test
    void platformOwner_canAccessOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform.totalTenants").isNumber());
    }

    @Test
    void landlordOwnerToken_isForbiddenOnOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void renterToken_isForbiddenOnOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequest_isForbiddenOnOverview() throws Exception {
        mockMvc.perform(get("/api/v1/admin/overview"))
                .andExpect(status().isForbidden());
    }

    // ---------- landlords ----------

    @Test
    void platformOwner_canListLandlords() throws Exception {
        mockMvc.perform(get("/api/v1/admin/landlords")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void landlordManagerToken_isForbiddenOnLandlords() throws Exception {
        mockMvc.perform(get("/api/v1/admin/landlords")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformOwner_unknownLandlordDetail_isNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/landlords/" + UNKNOWN_LANDLORD_ID)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void landlordOwnerToken_isForbiddenOnLandlordDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/landlords/" + UNKNOWN_LANDLORD_ID)
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    // ---------- commission ----------

    @Test
    void platformOwner_canReadCommissionStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/landlords/" + UNKNOWN_LANDLORD_ID + "/commission")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.landlordOrgId").value(UNKNOWN_LANDLORD_ID.toString()));
    }

    @Test
    void landlordOwnerToken_isForbiddenOnCommissionSet() throws Exception {
        mockMvc.perform(put("/api/v1/admin/landlords/" + UNKNOWN_LANDLORD_ID + "/commission")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratePercent\": 5.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordOwnerToken_isForbiddenOnCommissionClear() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/landlords/" + UNKNOWN_LANDLORD_ID + "/commission")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }
    // ---------- platform default commission ----------

    @Test
    void platformOwner_canReadDefaultCommission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/commission/default")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("DEFAULT"));
    }

    @Test
    void landlordOwnerToken_isForbiddenOnDefaultCommissionSet() throws Exception {
        mockMvc.perform(put("/api/v1/admin/commission/default")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ratePercent\": 5.00}"))
                .andExpect(status().isForbidden());
    }

    // ---------- platform logo (settings/logo) ----------

    @Test
    void platformOwner_canRemoveUnconfiguredLogo_idempotently() throws Exception {
        // No logo configured in the (empty) test DB — DELETE is idempotent and
        // does not touch Cloudinary, so it is safe to exercise in the full
        // Spring context.
        mockMvc.perform(delete("/api/v1/admin/settings/logo")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platform.logoUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void platformAdmin_isForbiddenOnLogoRemove() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/settings/logo")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_PLATFORM_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordOwnerToken_isForbiddenOnLogoUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/settings/logo")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "logo.png", "image/png", new byte[]{1, 2, 3}))
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordOwnerToken_isForbiddenOnLogoRemove() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/settings/logo")
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void renterToken_isForbiddenOnLogoUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/settings/logo")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "logo.png", "image/png", new byte[]{1, 2, 3}))
                        .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_TENANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequest_isForbiddenOnLogoUpload() throws Exception {
        mockMvc.perform(multipart("/api/v1/admin/settings/logo")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "logo.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isForbidden());
    }

    // ---------- public platform branding ----------

    @Test
    void publicBranding_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/public/platform/branding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.platformName").isString())
                .andExpect(jsonPath("$.data.logoUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.environment").isString());
    }

}
