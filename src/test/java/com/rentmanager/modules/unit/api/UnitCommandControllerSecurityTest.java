package com.rentmanager.modules.unit.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.unit.api.controller.UnitCommandController;
import com.rentmanager.modules.unit.application.command.service.UnitCommandService;
import com.rentmanager.modules.unit.application.dto.request.CreateUnitRequest;
import com.rentmanager.modules.unit.application.dto.request.UpdateUnitRequest;
import com.rentmanager.modules.unit.application.dto.response.UnitResponse;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Addendum 4 §1.2 acceptance criteria for UnitCommandController:
 *  - create/update/activate/archive: OWNER+MANAGER, STAFF -> 403
 *  - markOccupied/markVacant: OWNER, MANAGER, STAFF all succeed (same
 *    on-site-caretaker reasoning as RentLedger's ordinary rent recording)
 *
 * FINDING RESOLVED (Addendum 4 §1.4 / §4.4): UnitRoutes.DEACTIVATE is
 * confirmed orphaned, not a missing controller method. The real controller
 * has no /deactivate mapping at all -- only /activate, /archive, /occupied,
 * /vacant. This is a documentation note, not a code fix; the unused
 * constant itself is left untouched, consistent with the ground rule
 * against silently fixing flagged findings as a side effect of RBAC work.
 *
 * TENANT CONTEXT: unlike PropertyCommandController and
 * RentLedgerCommandController, this controller reads tenantId from
 * TenantContext.getTenantId() directly (confirmed from the pasted
 * controller source), NOT from @AuthenticationPrincipal. So this test
 * DOES need the @BeforeEach/@AfterEach TenantContext set/clear from the
 * original TenantControllerSecurityTest template -- carried back in here
 * deliberately, not by default.
 *
 * STATUS: Complete. UnitResponse.java (a plain POJO with a no-arg
 * constructor and setters -- no Lombok builder, no invariants) has been
 * provided and traced. All twelve tests are implemented below.
 *
 * @MockBean SET: UnitCommandController's only real constructor dependency
 * is UnitCommandService. The remaining six mocks are the same slice-wide
 * infrastructure beans required by GlobalExceptionHandler and the JWT
 * filter chain in every @WebMvcTest slice in this codebase.
 */
@WebMvcTest(controllers = UnitCommandController.class)
class UnitCommandControllerSecurityTest {

    private static final String UNITS_BASE = "/api/v1/units";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UnitCommandService unitCommandService;

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
    private static final UUID UNIT_ID = UUID.randomUUID();

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

    // No @NotNull/bean-validation constraints exist on CreateUnitRequest or
    // UpdateUnitRequest despite @Valid being present on the controller --
    // so minimal bodies are sufficient and deserialize without error.

    private String minimalCreateRequestJson() throws Exception {
        CreateUnitRequest request = CreateUnitRequest.builder()
                .propertyId(UUID.randomUUID())
                .unitNumber("A1")
                .label("Unit A1")
                .build();
        return objectMapper.writeValueAsString(request);
    }

    private String minimalUpdateRequestJson() throws Exception {
        UpdateUnitRequest request = UpdateUnitRequest.builder()
                .unitNumber("A1-updated")
                .build();
        return objectMapper.writeValueAsString(request);
    }

    // ================= STAFF forbidden on create/update/activate/archive =================

    @Test
    void staff_forbidden_onCreateUnit() throws Exception {
        mockMvc.perform(post(UNITS_BASE)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(minimalCreateRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_forbidden_onUpdateUnit() throws Exception {
        mockMvc.perform(put(UNITS_BASE + "/{unitId}", UNIT_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(minimalUpdateRequestJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_forbidden_onActivateUnit() throws Exception {
        mockMvc.perform(patch(UNITS_BASE + "/{unitId}/activate", UNIT_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void staff_forbidden_onArchiveUnit() throws Exception {
        mockMvc.perform(patch(UNITS_BASE + "/{unitId}/archive", UNIT_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ============= OWNER/MANAGER succeed on activate/archive (void -- safe) =============

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onActivateUnit(String authority) throws Exception {
        mockMvc.perform(patch(UNITS_BASE + "/{unitId}/activate", UNIT_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onArchiveUnit(String authority) throws Exception {
        mockMvc.perform(patch(UNITS_BASE + "/{unitId}/archive", UNIT_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ========= All three landlord roles succeed on markOccupied/markVacant (void) =========

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER", "ROLE_LANDLORD_STAFF"})
    void allLandlordRoles_succeed_onMarkOccupied(String authority) throws Exception {
        mockMvc.perform(patch(UNITS_BASE + "/{unitId}/occupied", UNIT_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER", "ROLE_LANDLORD_STAFF"})
    void allLandlordRoles_succeed_onMarkVacant(String authority) throws Exception {
        mockMvc.perform(patch(UNITS_BASE + "/{unitId}/vacant", UNIT_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ================= OWNER/MANAGER succeed on create/update =================
    // UnitResponse built via its no-arg constructor + setters before opening
    // each when() chain -- not inline as a .thenReturn() argument -- per the
    // nested-mock stubbing lesson from RentLedgerCommandControllerSecurityTest.

    private UnitResponse sampleUnitResponse() {
        UnitResponse response = new UnitResponse();
        response.setId(UNIT_ID);
        response.setTenantId(TENANT_ID);
        response.setUnitNumber("A1");
        return response;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onCreateUnit(String authority) throws Exception {
        UnitResponse response = sampleUnitResponse();
        when(unitCommandService.create(any(), any())).thenReturn(response);

        mockMvc.perform(post(UNITS_BASE)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(minimalCreateRequestJson()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onUpdateUnit(String authority) throws Exception {
        UnitResponse response = sampleUnitResponse();
        when(unitCommandService.update(any(), any(), any())).thenReturn(response);

        mockMvc.perform(put(UNITS_BASE + "/{unitId}", UNIT_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(minimalUpdateRequestJson()))
                .andExpect(status().isOk());
    }
}