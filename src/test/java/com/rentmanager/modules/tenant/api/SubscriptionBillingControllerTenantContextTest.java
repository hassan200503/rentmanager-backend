package com.rentmanager.modules.tenant.api;

import com.rentmanager.modules.tenant.api.controller.SubscriptionBillingController;
import com.rentmanager.modules.tenant.application.dto.response.SubscriptionStatusResponse;
import com.rentmanager.modules.tenant.application.service.SubscriptionBillingService;
import com.rentmanager.modules.tenant.domain.enums.BillingMode;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionPaymentPurpose;
import com.rentmanager.modules.tenant.domain.enums.SubscriptionStatus;
import com.rentmanager.modules.tenant.domain.model.SubscriptionPaymentRequest;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.jwt.JwtProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tenant-isolation guarantee for subscription state: the tenant is resolved
 * from TenantContext (server-side, from the authenticated JWT) ONLY - any
 * client-supplied plan/subscription header is ignored. Mirror of the
 * existing tenant-isolation fix posture.
 */
@WebMvcTest(controllers = SubscriptionBillingController.class)
@Import(SubscriptionBillingControllerTenantContextTest.PermitAllSecurityConfig.class)
class SubscriptionBillingControllerTenantContextTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String X_PLAN_HEADER = "X-Plan";

    @TestConfiguration
    static class PermitAllSecurityConfig {
        @Bean
        @Order(1)
        SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionBillingService subscriptionBillingService;

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

    @BeforeEach
    void setUp() {
        reset(subscriptionBillingService);
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getStatus_clientSuppliesPlanHeader_statusStillComesFromTenantContextOnly() throws Exception {
            SubscriptionStatusResponse commission =
                    new SubscriptionStatusResponse(
                            BillingMode.COMMISSION,
                            SubscriptionStatus.ACTIVE,
                            null, null, null, null, null, null, false,
                            "174379", "T-001", false, null, null);
            when(subscriptionBillingService.getStatus(TENANT_ID)).thenReturn(commission);

            mockMvc.perform(get("/api/v1/tenants/subscription")
                            .header(X_PLAN_HEADER, "PREMIUM_MONTHLY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.billingMode").value("COMMISSION"));

            verify(subscriptionBillingService).getStatus(TENANT_ID);
        }

        @Test
        void getStatus_withoutTenantContext_failsClosedWith403() throws Exception {
            TenantContext.clear();

            mockMvc.perform(get("/api/v1/tenants/subscription")
                            .header(X_PLAN_HEADER, "PREMIUM_MONTHLY"))
                    .andExpect(status().isForbidden());

            verify(subscriptionBillingService, never()).getStatus(any());
        }

        @Test
        void setupRatiba_clientSuppliesPlanHeader_tenantStillResolvedFromContext() throws Exception {
            when(subscriptionBillingService.setupRatibaStandingOrder(TENANT_ID))
                    .thenReturn(null);

            mockMvc.perform(post("/api/v1/tenants/subscription/ratiba")
                            .header(X_PLAN_HEADER, "COMMISSION"))
                    .andExpect(status().isOk());

            verify(subscriptionBillingService).setupRatibaStandingOrder(TENANT_ID);
    }

        @Test
        void getPaymentRequestStatus_tenantResolvedFromContextAndScopedToTenant() throws Exception {
            UUID requestId = UUID.randomUUID();
            SubscriptionPaymentRequest request = SubscriptionPaymentRequest.create(
                    TENANT_ID,
                    UUID.randomUUID(),
                    new java.math.BigDecimal("2500.00"),
                    "254712345678",
                    SubscriptionPaymentPurpose.INITIAL_ACTIVATION);
            when(subscriptionBillingService.getPaymentRequestStatus(TENANT_ID, requestId))
                    .thenReturn(request);

            mockMvc.perform(get("/api/v1/tenants/subscription/payments/" + requestId)
                            .header(X_PLAN_HEADER, "PREMIUM_MONTHLY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"));

            verify(subscriptionBillingService).getPaymentRequestStatus(TENANT_ID, requestId);
        }

        @Test
        void getPaymentRequestStatus_withoutTenantContext_failsClosedWith403() throws Exception {
            TenantContext.clear();

            mockMvc.perform(get("/api/v1/tenants/subscription/payments/" + UUID.randomUUID()))
                    .andExpect(status().isForbidden());

            verify(subscriptionBillingService, never()).getPaymentRequestStatus(any(), any());
        }
}
