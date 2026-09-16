package com.rentmanager.modules.platformadmin.api;

import com.rentmanager.modules.platformadmin.api.controller.PlatformAdminController;
import com.rentmanager.modules.platformadmin.application.service.PlatformAdminQueryService;
import com.rentmanager.modules.rentledger.application.service.CommissionPolicyService;
import com.rentmanager.modules.tenant.application.service.SubscriptionBillingService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC for the admin rent-payment queue.
 *
 * <p>This endpoint exists because the admin overview's alert panel linked
 * "failed payments" and "payments pending" to {@code /admin/payments}, a page
 * that could not be built: nothing could list the rows behind the counter. An
 * admin who saw a problem and clicked it got a 404 at the moment they most
 * needed to act.
 *
 * <p>It is <strong>cross-tenant by design</strong> — the platform view — so
 * unlike a landlord endpoint there is no {@code TenantContext} scoping to
 * assert. The role gate is the entire control, which makes these tests the
 * only thing standing between a landlord token and every renter's payment
 * across the platform.
 */
@WebMvcTest(controllers = PlatformAdminController.class)
class AdminPaymentRequestsSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PlatformAdminQueryService queryService;

    @MockBean
    private CommissionPolicyService commissionPolicyService;

    @MockBean
    private com.rentmanager.modules.platformadmin.application.service.PlatformAdminCommissionService platformAdminCommissionService;

    @MockBean
    private ErrorTrackingService errorTrackingService;

    @MockBean
    private SubscriptionBillingService subscriptionBillingService;

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
                UUID.randomUUID(), null, "admin@example.com", "", true, authorities);

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    private void queueReturnsEmpty() {
        when(queryService.getPaymentRequests(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    // ── Permitted ────────────────────────────────────────────────────────

    @Test
    void platformOwner_canReadTheQueue() throws Exception {
        queueReturnsEmpty();

        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .with(authentication(token("ROLE_PLATFORM_OWNER"))))
                .andExpect(status().isOk());
    }

    @Test
    void platformAdmin_canReadTheQueue() throws Exception {
        queueReturnsEmpty();

        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .with(authentication(token("ROLE_PLATFORM_ADMIN"))))
                .andExpect(status().isOk());
    }

    // ── Refused ──────────────────────────────────────────────────────────

    /**
     * A landlord reaching this endpoint would see every other landlord's rent
     * payments. This is the assertion that matters most in the file.
     */
    @Test
    void landlordOwner_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .with(authentication(token("ROLE_LANDLORD_OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void landlordManager_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .with(authentication(token("ROLE_LANDLORD_MANAGER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .with(authentication(token("ROLE_TENANT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingOnboarding_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .with(authentication(token("ROLE_PENDING_ONBOARDING"))))
                .andExpect(status().isForbidden());
    }

    // ── Filters ──────────────────────────────────────────────────────────

    @Test
    void statusFilterReachesTheQuery() throws Exception {
        queueReturnsEmpty();

        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .param("status", "FAILED")
                        .with(authentication(token("ROLE_PLATFORM_OWNER"))))
                .andExpect(status().isOk());

        verify(queryService).getPaymentRequests(
                isNull(), eq(RentPaymentRequestStatus.FAILED), any());
    }

    @Test
    void landlordFilterReachesTheQuery() throws Exception {
        UUID landlordId = UUID.randomUUID();
        queueReturnsEmpty();

        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .param("landlordId", landlordId.toString())
                        .with(authentication(token("ROLE_PLATFORM_OWNER"))))
                .andExpect(status().isOk());

        verify(queryService).getPaymentRequests(eq(landlordId), isNull(), any());
    }

    /**
     * An unbounded page size on a cross-tenant query is a way to pull every
     * payment on the platform in one request.
     */
    @Test
    void pageSizeIsCappedRegardlessOfWhatIsAskedFor() throws Exception {
        queueReturnsEmpty();

        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .param("size", "100000")
                        .with(authentication(token("ROLE_PLATFORM_OWNER"))))
                .andExpect(status().isOk());

        verify(queryService).getPaymentRequests(
                isNull(), isNull(),
                org.mockito.ArgumentMatchers.argThat(p -> p.getPageSize() <= 100));
    }

    @Test
    void aNonsensicalPageSizeDoesNotProduceAnInvalidPageable() throws Exception {
        queueReturnsEmpty();

        mockMvc.perform(get("/api/v1/admin/payment-requests")
                        .param("size", "0")
                        .with(authentication(token("ROLE_PLATFORM_OWNER"))))
                .andExpect(status().isOk());

        verify(queryService).getPaymentRequests(
                isNull(), isNull(),
                org.mockito.ArgumentMatchers.argThat(p -> p.getPageSize() >= 1));
    }
}
