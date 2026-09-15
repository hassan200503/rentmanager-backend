package com.rentmanager.modules.tenant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.RentManagerApplication;
import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.support.MockTenantAuthentication;
import com.rentmanager.modules.support.TestSecurityConfig;
import com.rentmanager.modules.tenant.application.command.service.TenantCommandService;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import com.rentmanager.shared.security.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers TenantController's createTenant() and getTenant() endpoints —
 * the two that use resolveStrictTenantId() but carry NO @PreAuthorize
 * gate (unlike configureDarajaCredentials/suspendTenant/activateTenant,
 * which are covered separately and thoroughly by
 * TenantControllerSecurityTest).
 *
 * ---- Rewritten from scratch (previous version was broken) ----
 *
 * The prior version of this test authenticated via a raw
 * .header("X-Tenant-Id", ...) call with NO real Authentication object and
 * NO TenantContext population. TenantController.resolveStrictTenantId()
 * (added when createTenant/getTenant were changed from the lenient
 * resolveTenantId() fallback to a fail-closed strict resolver — see
 * project handoff) reads exclusively from TenantContext, which is
 * populated server-side from a verified Clerk JWT — never from a client
 * header. So the old test's "authentication" was a no-op, and the
 * request would actually have been rejected by resolveStrictTenantId()
 * with a 403 rather than succeeding as asserted. This version uses
 * MockTenantAuthentication (the same real mechanism LeaseApiTest and
 * PropertyApiTest use), which populates both SecurityContextHolder and
 * TenantContext directly, exactly standing in for what
 * ClerkJwtAuthenticationConverter does in production.
 *
 * KNOWN CALLER-STATUS CAVEAT (carried forward from project handoff): as
 * of the last full audit, no frontend code and no backend Clerk-webhook
 * handler was found calling POST /api/tenants or GET /api/tenants/{id}
 * outside generated OpenAPI type stubs. This endpoint's real-world usage
 * is still an open question with the project owner. This test suite
 * exists as defensive regression coverage for the endpoint AS WRITTEN,
 * independent of that open question — if the endpoint is later
 * confirmed fully unused and removed, this test class should be removed
 * alongside it, not left stranded.
 *
 * SCOPE: this class deliberately does NOT re-test @PreAuthorize
 * authorization gates (TenantControllerSecurityTest already covers
 * configureDarajaCredentials/suspendTenant/activateTenant thoroughly).
 * It covers three things nothing else in the suite currently covers:
 *   1. The happy path for createTenant() with a real resolved tenant.
 *   2. resolveStrictTenantId()'s fail-closed behavior when NO tenant
 *      context is present at all (never previously tested against a
 *      real request — TenantControllerSecurityTest always sets
 *      TenantContext in @BeforeEach).
 *   3. The SecurityException -> 403 mapping in GlobalExceptionHandler,
 *      which its own javadoc says exists specifically for cross-tenant
 *      access denials thrown by TenantCommandServiceImpl.validateTenantAccess()
 *      — documented but, prior to this test, never actually exercised
 *      end-to-end through a real MockMvc request.
 *
 * NOTE ON @ContextConfiguration: this class boots a full @SpringBootTest
 * context (Flyway included) even though TenantCommandService is mocked,
 * because the mock only replaces the service bean — the surrounding
 * context, including the real datasource, still starts. Like
 * LeaseApiTest and PropertyApiTest, it needs PostgresSpringBridge as an
 * initializer so Flyway/Hikari connect to the actual Testcontainers
 * instance instead of falling back to application-test.yml's static
 * (and invalid, against a real container) credentials.
 */
@SpringBootTest(classes = RentManagerApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
class TenantApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantCommandService tenantCommandService;

    // TenantContext is backed by a ThreadLocal, and Surefire reuses the
    // same test thread across methods within this class — clear it after
    // every test so no test's tenant context leaks into the next. Same
    // reasoning as TenantControllerSecurityTest.
    @AfterEach
    void tearDownTenantContext() {
        TenantContext.clear();
    }

    private static final String CREATE_TENANT_REQUEST_JSON = """
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

    private TenantResponse buildTenantResponse(UUID tenantId) {
        TenantResponse response = new TenantResponse();
        response.setTenantId(tenantId);
        response.setName("API Tenant");
        response.setEmail("api@tenant.com");
        response.setPhoneNumber("0800000000");
        response.setAddress("N/A");
        response.setStatus("ACTIVE");
        return response;
    }

    // ---- 1. Happy path: real authenticated tenant context ----

    @Test
    void should_create_tenant_when_tenant_context_resolves() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantResponse response = buildTenantResponse(tenantId);

        when(tenantCommandService.createTenant(eq(tenantId), any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/tenants")
                        .with(MockTenantAuthentication.asTenant(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_TENANT_REQUEST_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").exists())
                .andExpect(jsonPath("$.data.name").value("API Tenant"))
                .andExpect(jsonPath("$.data.email").value("api@tenant.com"))
                .andExpect(jsonPath("$.data.phoneNumber").value("0800000000"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    // ---- 2. Fail-closed: no tenant context at all ----

    @Test
    void should_reject_createTenant_when_no_tenant_context_present() throws Exception {
        // Deliberately NOT using MockTenantAuthentication.asTenant(...) —
        // this simulates a request where JWT verification/conversion
        // never populated TenantContext (e.g. a malformed or missing
        // token that somehow still reached the controller). Combined
        // with TestSecurityConfig's permitAll(), this is the only way to
        // reach resolveStrictTenantId() with a genuinely empty context
        // and prove it rejects rather than silently falling back.
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_TENANT_REQUEST_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void should_reject_getTenant_when_no_tenant_context_present() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/{tenantId}", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    // ---- 3. Happy path + cross-tenant denial for getTenant ----

    @Test
    void should_get_tenant_when_caller_owns_it() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantResponse response = buildTenantResponse(tenantId);

        when(tenantCommandService.getTenant(eq(tenantId), eq(tenantId)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/tenants/{tenantId}", tenantId)
                        .with(MockTenantAuthentication.asTenant(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantId").exists());
    }

    @Test
    void should_reject_getTenant_across_tenants() throws Exception {
        UUID callerTenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();

        // Exercises the GlobalExceptionHandler.handleSecurityException()
        // mapping documented in that class's own javadoc: real
        // cross-tenant denials from TenantCommandServiceImpl.validateTenantAccess()
        // throw plain java.lang.SecurityException, which must map to 403
        // — not the generic 500 fallback. Never previously exercised
        // end-to-end through an actual MockMvc request in this suite.
        when(tenantCommandService.getTenant(eq(callerTenantId), eq(otherTenantId)))
                .thenThrow(new SecurityException(
                        "Cross-tenant access denied for tenant: " + otherTenantId));

        mockMvc.perform(get("/api/v1/tenants/{tenantId}", otherTenantId)
                        .with(MockTenantAuthentication.asTenant(callerTenantId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
}