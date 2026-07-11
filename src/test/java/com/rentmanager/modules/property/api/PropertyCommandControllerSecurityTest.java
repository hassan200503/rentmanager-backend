package com.rentmanager.modules.property.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.property.api.controller.PropertyCommandController;
import com.rentmanager.modules.property.application.command.service.PropertyCommandService;
import com.rentmanager.modules.property.application.dto.request.CreatePropertyRequest;
import com.rentmanager.modules.property.application.dto.request.UpdatePropertyRequest;
import com.rentmanager.modules.property.application.dto.response.PropertyResponse;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Addendum 4 §1.2 acceptance criteria for PropertyCommandController:
 * all four mutating endpoints (create/update/activate/archive) are gated
 * OWNER+MANAGER, STAFF excluded across the whole controller -- no
 * on-site/operational analog exists for property lifecycle actions (unlike
 * Unit's occupancy toggles), so unlike RentLedger this controller has no
 * "all three roles succeed" endpoint to cover.
 *
 * STATUS: Complete. PropertyResponse.java (a plain @Builder @Getter class,
 * not a record -- no invariants, so a real instance is built directly via
 * its builder rather than mocked) and PropertyRoutes.java (confirms BASE =
 * "/api/v1/properties", matching what was assumed from Addendum 4) have
 * both been provided and traced. All eight tests (4 STAFF-forbidden,
 * 4 OWNER/MANAGER success) are implemented below.
 *
 * @MockBean SET: PropertyCommandController's only real constructor
 * dependency is PropertyCommandService (the interface, not Impl). The
 * remaining six mocks are the same slice-wide infrastructure beans
 * required by GlobalExceptionHandler and the JWT filter chain in every
 * @WebMvcTest slice in this codebase, per TenantControllerSecurityTest /
 * RentLedgerCommandControllerSecurityTest.
 *
 * No TenantContext @BeforeEach/@AfterEach: PropertyCommandController.
 * requireTenantId() reads tenantId from AuthenticatedUser.getTenantId()
 * directly (confirmed from the controller's own pasted source), same as
 * RentLedgerCommandController -- not from the TenantContext ThreadLocal.
 */
@WebMvcTest(controllers = PropertyCommandController.class)
class PropertyCommandControllerSecurityTest {

    private static final String PROPERTIES_BASE = "/api/v1/properties";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PropertyCommandService propertyCommandService;

    @MockBean
    private ErrorTrackingService errorTrackingService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private SecurityContextService securityContextService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private TenantProfileRepository tenantProfileRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();

    // No TenantContext @BeforeEach/@AfterEach here -- see class javadoc.

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

    // Minimal bodies are deliberate and sufficient: neither request DTO has
    // @NotNull/bean-validation annotations, and the controller does not put
    // @Valid on either @RequestBody -- so partial/null nested fields
    // deserialize without error, and since propertyCommandService is
    // mocked, Property.create()'s real invariant checks never run anyway.

    private String minimalCreateRequestJson() throws Exception {
        CreatePropertyRequest request = new CreatePropertyRequest();
        request.setName("Test Property");
        return objectMapper.writeValueAsString(request);
    }

    private String minimalUpdateRequestJson() throws Exception {
        UpdatePropertyRequest request = new UpdatePropertyRequest();
        request.setName("Updated Name");
        return objectMapper.writeValueAsString(request);
    }

    // ================= STAFF forbidden on all four endpoints =================

    @Test
    void staff_forbidden_onCreateProperty() throws Exception {
        mockMvc.perform(post(PROPERTIES_BASE)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(minimalCreateRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_forbidden_onUpdateProperty() throws Exception {
        mockMvc.perform(put(PROPERTIES_BASE + "/{propertyId}", PROPERTY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(minimalUpdateRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_forbidden_onActivateProperty() throws Exception {
        mockMvc.perform(post(PROPERTIES_BASE + "/{propertyId}/activate", PROPERTY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_forbidden_onArchiveProperty() throws Exception {
        mockMvc.perform(post(PROPERTIES_BASE + "/{propertyId}/archive", PROPERTY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ================= OWNER/MANAGER succeed on all four endpoints =================
    // PropertyResponse built via its real builder before opening each when()
    // chain -- not inline as a .thenReturn() argument -- per the nested-mock
    // stubbing lesson from RentLedgerCommandControllerSecurityTest (calling
    // a mock-producing helper as a .thenReturn() argument while the outer
    // when() is still open corrupts Mockito's stubbing state). Not actually
    // a mock here since PropertyResponse is built directly, but keeping the
    // same "build first, stub second" ordering as a deliberate habit.

    private PropertyResponse samplePropertyResponse() {
        return PropertyResponse.builder()
                .propertyId(PROPERTY_ID)
                .tenantId(TENANT_ID)
                .name("Test Property")
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onCreateProperty(String authority) throws Exception {
        PropertyResponse response = samplePropertyResponse();
        when(propertyCommandService.createProperty(any(), any())).thenReturn(response);

        mockMvc.perform(post(PROPERTIES_BASE)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(minimalCreateRequestJson()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onUpdateProperty(String authority) throws Exception {
        PropertyResponse response = samplePropertyResponse();
        when(propertyCommandService.updateProperty(any(), any(), any())).thenReturn(response);

        mockMvc.perform(put(PROPERTIES_BASE + "/{propertyId}", PROPERTY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(minimalUpdateRequestJson()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onActivateProperty(String authority) throws Exception {
        PropertyResponse response = samplePropertyResponse();
        when(propertyCommandService.activateProperty(any(), any())).thenReturn(response);

        mockMvc.perform(post(PROPERTIES_BASE + "/{propertyId}/activate", PROPERTY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onArchiveProperty(String authority) throws Exception {
        PropertyResponse response = samplePropertyResponse();
        when(propertyCommandService.archiveProperty(any(), any())).thenReturn(response);

        mockMvc.perform(post(PROPERTIES_BASE + "/{propertyId}/archive", PROPERTY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }
}