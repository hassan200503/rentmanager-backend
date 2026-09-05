package com.rentmanager.modules.unit.api;

import com.rentmanager.modules.unit.api.controller.UnitQueryController;
import com.rentmanager.modules.unit.application.query.service.UnitQueryService;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.junit.jupiter.api.Test;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Role gate on the landlord unit read API.
 *
 * <p>This controller had no {@code @PreAuthorize} on any of its seven read
 * methods, while its sibling {@code UnitCommandController} gated all of its
 * own. The project rule is that a controller touching tenant data carries the
 * annotation <em>and</em> scopes by {@code TenantContext} — both, always.
 *
 * <p>The missing half was not exploitable: {@code TenantContext.getTenantId()}
 * throws when nothing is bound, so an unbound caller failed rather than
 * reading another landlord's units. These tests assert the intended outcome —
 * a clean 403 — instead of relying on that throw, which a single refactor to
 * {@code getTenantIdOrNull()} would remove.
 */
@WebMvcTest(controllers = UnitQueryController.class)
class UnitQueryControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UnitQueryService unitQueryService;

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

    private AbstractAuthenticationToken token(String authority) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", "clerk_user_id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Set<? extends GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority(authority));
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(), UUID.randomUUID(), "user@example.com", "", true, authorities);

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    /**
     * A renter must not reach the landlord unit API at all. Before the gate
     * they got an exception from the unbound tenant context; now they get the
     * answer the authorisation model intends.
     */
    @Test
    void renter_isForbiddenFromTheOccupancyAggregate() throws Exception {
        mockMvc.perform(get("/api/v1/units/occupancy-by-property")
                        .with(authentication(token("ROLE_TENANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_isForbiddenFromTheUnitList() throws Exception {
        mockMvc.perform(get("/api/v1/units")
                        .with(authentication(token("ROLE_TENANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_isForbiddenFromTheUnitSummary() throws Exception {
        mockMvc.perform(get("/api/v1/units/summary")
                        .with(authentication(token("ROLE_TENANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingOnboarding_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/units/occupancy-by-property")
                        .with(authentication(token("ROLE_PENDING_ONBOARDING"))))
                .andExpect(status().isForbidden());
    }

    /**
     * STAFF reads on purpose: a caretaker needs to see the units they look
     * after. Creating and editing them is a separate decision, which is why
     * the command controller stops at MANAGER.
     */
    @Test
    void staff_mayRead() throws Exception {
        mockMvc.perform(get("/api/v1/units/occupancy-by-property")
                        .with(authentication(token("ROLE_LANDLORD_STAFF"))))
                .andExpect(status().isOk());
    }

    @Test
    void manager_mayRead() throws Exception {
        mockMvc.perform(get("/api/v1/units/occupancy-by-property")
                        .with(authentication(token("ROLE_LANDLORD_MANAGER"))))
                .andExpect(status().isOk());
    }

    @Test
    void owner_mayRead() throws Exception {
        mockMvc.perform(get("/api/v1/units/occupancy-by-property")
                        .with(authentication(token("ROLE_LANDLORD_OWNER"))))
                .andExpect(status().isOk());
    }
}
