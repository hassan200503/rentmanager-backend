package com.rentmanager.modules.tenant.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.tenant.api.controller.TenantController;
import com.rentmanager.modules.tenant.application.command.service.TenantCommandService;
import com.rentmanager.modules.tenant.application.dto.request.ConfigureDarajaCredentialsRequest;
import com.rentmanager.modules.tenant.application.dto.request.SuspendTenantRequest;
import com.rentmanager.modules.tenant.application.dto.response.DarajaCredentialsStatusResponse;
import com.rentmanager.modules.tenant.application.dto.response.TenantResponse;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import com.rentmanager.shared.error.ErrorTrackingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers §6.2 acceptance criteria for TenantControllerSecurityTest from the
 * RBAC handoff doc:
 *  - STAFF and MANAGER authorities -> 403 on all three OWNER-gated endpoints
 *  - OWNER authority -> 200 on all three (with TenantCommandService mocked)
 *
 * NOTE ON METHOD SECURITY WIRING: this project's real SecurityConfig was
 * not shown in this conversation, so this test declares its own minimal
 * @EnableMethodSecurity test configuration to guarantee @PreAuthorize is
 * actually enforced inside this @WebMvcTest slice. If the application
 * already wires method security differently (a different mode, or via a
 * shared test security config elsewhere in the suite), this local
 * configuration should be reconciled with that rather than duplicated.
 */
@WebMvcTest(controllers = TenantController.class)
class TenantControllerSecurityTest {

    // FIX: TenantController is @RequestMapping("/api/v1/tenants") (confirmed
    // against the actual controller source). Every request path below was
    // "/api/tenants/..." — missing "/v1" — which would 404 rather than hit
    // @PreAuthorize at all, silently defeating every assertion in this class.
    private static final String TENANTS_BASE = "/api/v1/tenants";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TenantCommandService tenantCommandService;

    // GlobalExceptionHandler (a @ControllerAdvice, loaded even in this
    // @WebMvcTest slice) requires ErrorTrackingService in its constructor.
    // The real implementation isn't needed here — none of these tests
    // exercise error-tracking behavior — this just satisfies Spring's
    // dependency resolution so the context can start.
    @MockBean
    private ErrorTrackingService errorTrackingService;

    // JwtAuthenticationFilter (part of the security filter chain, loaded
    // even in this @WebMvcTest slice) requires JwtProvider and
    // SecurityContextService in its constructor. These tests bypass the
    // filter chain's normal token extraction by injecting authentication
    // directly via SecurityMockMvcRequestPostProcessors.authentication(...),
    // so the real behavior of these collaborators is never exercised here —
    // these mocks just satisfy Spring's dependency resolution so the
    // context can start.
    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private SecurityContextService securityContextService;

    // ClerkJwtAuthenticationConverter (another security-related bean wired
    // into the app's SecurityConfig / OAuth2 resource server setup) requires
    // UserRepository, TenantRepository, and TenantProfileRepository in its
    // constructor. Not exercised by these tests since authentication is
    // injected directly (the converter's convert() method never runs), but
    // all three are needed purely for context startup / bean resolution.
    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private TenantProfileRepository tenantProfileRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();

    // TenantContext is populated by ClerkJwtAuthenticationConverter.convert()
    // in production, but these tests bypass the filter chain entirely by
    // injecting Authentication directly via
    // SecurityMockMvcRequestPostProcessors.authentication(...), so convert()
    // never runs. Without this, TenantController.resolveStrictTenantId()
    // (used by all three endpoints under test) throws IllegalStateException
    // -> AccessDeniedException -> 403, masking the authorization check this
    // test class actually exists to verify. TenantContext is backed by a
    // ThreadLocal, and Surefire reuses the same test thread across methods,
    // so it must be cleared after each test to avoid leaking state.
    @BeforeEach
    void setUpTenantContext() {
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDownTenantContext() {
        TenantContext.clear();
    }

    private AbstractAuthenticationToken tokenWithAuthority(String authority) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", "clerk_user_id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Set<? extends GrantedAuthority> authorities = Set.of(
                new SimpleGrantedAuthority("ROLE_LANDLORD"),
                new SimpleGrantedAuthority(authority)
        );

        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(),
                TENANT_ID,
                "user@example.com",
                "",
                true,
                authorities
        );

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    private String validDarajaRequestJson() throws Exception {
        ConfigureDarajaCredentialsRequest request = new ConfigureDarajaCredentialsRequest();
        request.setConsumerKey("consumer-key");
        request.setConsumerSecret("consumer-secret");
        request.setBusinessShortCode("174379");
        request.setPasskey("passkey-value");
        return objectMapper.writeValueAsString(request);
    }

    private String validSuspendRequestJson() throws Exception {
        SuspendTenantRequest request = new SuspendTenantRequest();
        request.setReason("Non-payment of subscription fees");
        return objectMapper.writeValueAsString(request);
    }

    // --- STAFF / MANAGER forbidden on all three endpoints ---

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_STAFF", "ROLE_LANDLORD_MANAGER"})
    void nonOwner_forbidden_onDarajaCredentials(String authority) throws Exception {
        mockMvc.perform(put(TENANTS_BASE + "/{tenantId}/daraja-credentials", TENANT_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validDarajaRequestJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_STAFF", "ROLE_LANDLORD_MANAGER"})
    void nonOwner_forbidden_onSuspend(String authority) throws Exception {
        mockMvc.perform(put(TENANTS_BASE + "/{tenantId}/suspend", TENANT_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validSuspendRequestJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_STAFF", "ROLE_LANDLORD_MANAGER"})
    void nonOwner_forbidden_onActivate(String authority) throws Exception {
        mockMvc.perform(put(TENANTS_BASE + "/{tenantId}/activate", TENANT_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- OWNER succeeds on all three endpoints ---

    @Test
    void owner_succeeds_onDarajaCredentials() throws Exception {
        when(tenantCommandService.configureDarajaCredentials(any(), any(), any()))
                .thenReturn(new DarajaCredentialsStatusResponse(true, "DIRECT"));

        mockMvc.perform(put(TENANTS_BASE + "/{tenantId}/daraja-credentials", TENANT_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validDarajaRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void owner_succeeds_onSuspend() throws Exception {
        TenantResponse response = new TenantResponse();
        response.setTenantId(TENANT_ID);
        response.setStatus("SUSPENDED");
        when(tenantCommandService.suspendTenant(any(), any(), any())).thenReturn(response);

        mockMvc.perform(put(TENANTS_BASE + "/{tenantId}/suspend", TENANT_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validSuspendRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void owner_succeeds_onActivate() throws Exception {
        TenantResponse response = new TenantResponse();
        response.setTenantId(TENANT_ID);
        response.setStatus("ACTIVE");
        when(tenantCommandService.activateTenant(any(), any())).thenReturn(response);

        mockMvc.perform(put(TENANTS_BASE + "/{tenantId}/activate", TENANT_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}