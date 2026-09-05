package com.rentmanager.modules.property.api;

import com.rentmanager.modules.property.api.controller.PropertyMediaQueryController;
import com.rentmanager.modules.property.application.dto.response.PropertyMediaResponse;
import com.rentmanager.modules.property.application.query.service.PropertyMediaQueryService;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link PropertyMediaQueryController} carried no {@code @PreAuthorize} —
 * same gap as {@code UnitMediaController} (see that controller's Javadoc),
 * found while fixing it. Gated OWNER/MANAGER/STAFF, matching
 * {@code UnitQueryController}'s read gate.
 */
@WebMvcTest(controllers = PropertyMediaQueryController.class)
class PropertyMediaQueryControllerSecurityTest {

    private static final String PROPERTIES_BASE = "/api/v1/properties";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PropertyMediaQueryService propertyMediaQueryService;

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
                UUID.randomUUID(), TENANT_ID, "user@example.com", "", true, authorities);

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    @Test
    void renter_forbidden_onGetMedia() throws Exception {
        mockMvc.perform(get(PROPERTIES_BASE + "/{propertyId}/media", PROPERTY_ID)
                        .with(authentication(token("ROLE_TENANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingOnboarding_forbidden_onGetMedia() throws Exception {
        mockMvc.perform(get(PROPERTIES_BASE + "/{propertyId}/media", PROPERTY_ID)
                        .with(authentication(token("ROLE_PENDING_ONBOARDING"))))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER", "ROLE_LANDLORD_STAFF"})
    void allLandlordRoles_succeed_onGetMedia(String authority) throws Exception {
        when(propertyMediaQueryService.getPropertyMedia(any(), any())).thenReturn(List.of(
                new PropertyMediaResponse(UUID.randomUUID(), PROPERTY_ID, "https://example.com/x.jpg",
                        "x.jpg", "IMAGE", false, null, 0)
        ));

        mockMvc.perform(get(PROPERTIES_BASE + "/{propertyId}/media", PROPERTY_ID)
                        .with(authentication(token(authority))))
                .andExpect(status().isOk());
    }
}
