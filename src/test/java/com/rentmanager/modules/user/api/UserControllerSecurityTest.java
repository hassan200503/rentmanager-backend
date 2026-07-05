package com.rentmanager.modules.user.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.application.command.service.UserCommandService;
import com.rentmanager.modules.user.application.dto.request.InviteUserRequest;
import com.rentmanager.modules.user.application.dto.response.InviteUserResponse;
import com.rentmanager.modules.user.domain.model.UserRole;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import com.rentmanager.shared.error.ErrorTrackingService;
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
import java.util.UUID;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers §6.2 acceptance criteria for UserControllerSecurityTest from the
 * RBAC handoff doc:
 *  - STAFF authority -> 403 on POST /api/users/invite
 *  - OWNER and MANAGER authorities -> 200, passing the coarse controller
 *    gate (with UserCommandService mocked). The fine-grained matrix
 *    rejection (e.g. MANAGER inviting above STAFF) is already covered at
 *    the service-test level in UserCommandServiceImplTest and is
 *    deliberately not re-tested here.
 *
 * Same method-security wiring note as TenantControllerSecurityTest applies:
 * the real SecurityConfig was not shown in this conversation, so this
 * slice test supplies its own @EnableMethodSecurity test config.
 */
@WebMvcTest(controllers = UserController.class)
class UserControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserCommandService userCommandService;

    // Same reason as TenantControllerSecurityTest: GlobalExceptionHandler
    // is a @ControllerAdvice loaded by this @WebMvcTest slice, and its
    // constructor requires ErrorTrackingService.
    @MockBean
    private ErrorTrackingService errorTrackingService;

    // ClerkJwtAuthenticationConverter implements Converter<...>, which is
    // one of @WebMvcTest's default-included stereotypes, so it is
    // instantiated for real in this slice (not mocked itself). Its
    // constructor requires JwtProvider, SecurityContextService (via the
    // wider JWT filter infrastructure) and UserRepository, TenantRepository,
    // TenantProfileRepository (its own direct dependencies). None of these
    // are exercised by these tests — authentication is injected directly
    // via SecurityMockMvcRequestPostProcessors.authentication(...), so
    // convert() never runs — these mocks exist purely so the context can
    // start. See TenantControllerSecurityTest for the identical wiring.
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
                UUID.randomUUID(),
                "inviter@example.com",
                "",
                true,
                authorities
        );

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    private String validInviteRequestJson() throws Exception {
        InviteUserRequest request = new InviteUserRequest();
        request.setFirstName("New");
        request.setLastName("Hire");
        request.setEmail("newhire@example.com");
        request.setPhone("+254700000000");
        request.setRole(UserRole.STAFF);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void staff_forbidden_onInvite() throws Exception {
        mockMvc.perform(post("/api/users/invite")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validInviteRequestJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerOrManager_passesCoarseGate_onInvite(String authority) throws Exception {
        when(userCommandService.inviteUser(any()))
                .thenReturn(new InviteUserResponse(UUID.randomUUID(), "newhire@example.com", UserRole.STAFF));

        mockMvc.perform(post("/api/users/invite")
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validInviteRequestJson()))
                .andExpect(status().isOk());
    }
}