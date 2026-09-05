package com.rentmanager.modules.rentledger.api;

import com.rentmanager.modules.rentledger.api.controller.TenantPortalController;
import com.rentmanager.modules.rentledger.api.dto.response.RentPaymentRequestResponse;
import com.rentmanager.modules.rentledger.api.dto.response.TenantDashboardResponse;
import com.rentmanager.modules.rentledger.application.service.TenantPortalService;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC coverage for the class-level {@code @PreAuthorize("hasAuthority('ROLE_TENANT')")}
 * added to TenantPortalController. Only a renter (ROLE_TENANT) may reach any
 * method on this controller — a landlord-side caller (ROLE_LANDLORD_OWNER
 * etc.) must get 403 even though they are otherwise fully authenticated, and
 * an onboarding-pending caller (ROLE_PENDING_ONBOARDING — authenticated via
 * Clerk but bound to neither a landlord tenant nor a TenantProfile) must also
 * get 403. Two representative endpoints are exercised (one GET, one mutating
 * POST) since the annotation is class-level and therefore identical across
 * every method — per-endpoint exhaustive coverage would be redundant.
 *
 * Test wiring mirrors RentPaymentControllerTest.java / LeaseControllerSecurityTest.java
 * exactly (same package, same slice-wide mock beans, same
 * @TestConfiguration @EnableMethodSecurity nested class — @WebMvcTest does not
 * load arbitrary @Configuration classes, so @PreAuthorize is a no-op without it).
 */
@WebMvcTest(controllers = TenantPortalController.class)
class TenantPortalControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantPortalService tenantPortalService;

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

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    private AbstractAuthenticationToken tokenWithAuthority(String authority) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", "clerk_user_id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Set<? extends GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority(authority));

        AuthenticatedUser principal = new AuthenticatedUser(
                USER_ID,
                null,
                "renter@example.com",
                "",
                true,
                authorities
        );

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    private TenantDashboardResponse mockDashboardResponse() {
        return new TenantDashboardResponse(
                UUID.randomUUID(), "Jane Renter", "+254712345678", "jane@example.com",
                BigDecimal.ZERO, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
                "ACTIVE", "A1", "Sunset Apartments", new BigDecimal("15000.00"),
                new BigDecimal("15000.00"), List.of()
        );
    }

    private String initiatePaymentRequestJson() {
        return """
                {
                  "amount": 15000.00,
                  "mpesaPhone": "0712345678"
                }
                """;
    }

    // =====================================================================
    // GET /dashboard -- ROLE_TENANT succeeds; every other authority 403
    // =====================================================================

    @Test
    void getDashboard_renter_succeeds() throws Exception {
        when(tenantPortalService.getDashboard(any())).thenReturn(mockDashboardResponse());

        mockMvc.perform(get("/api/v1/tenant-portal/dashboard")
                        .with(authentication(tokenWithAuthority("ROLE_TENANT"))))
                .andExpect(status().isOk());
    }

    @Test
    void getDashboard_landlordOwner_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/tenant-portal/dashboard")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_pendingOnboarding_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/tenant-portal/dashboard")
                        .with(authentication(tokenWithAuthority("ROLE_PENDING_ONBOARDING"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDashboard_unauthenticated_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/tenant-portal/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // POST /rent-payments/initiate -- same gate on a mutating, body-carrying
    // endpoint, with CSRF applied (CsrfFilter runs before @PreAuthorize).
    // =====================================================================

    @Test
    void initiatePortalPayment_renter_succeeds() throws Exception {
        RentPaymentRequestResponse response = new RentPaymentRequestResponse(
                REQUEST_ID, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("15000.00"),
                RentPaymentRequestStatus.PENDING, null, null
        );
        when(tenantPortalService.initiatePortalPayment(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/tenant-portal/rent-payments/initiate")
                        .with(authentication(tokenWithAuthority("ROLE_TENANT")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiatePaymentRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void initiatePortalPayment_landlordOwner_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/tenant-portal/rent-payments/initiate")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initiatePaymentRequestJson()))
                .andExpect(status().isForbidden());
    }
}
