package com.rentmanager.modules.tenant.api;

import com.rentmanager.modules.tenant.api.controller.C2BConfirmationCallbackController;
import com.rentmanager.modules.tenant.application.service.SubscriptionC2bPaymentService;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.JwtProvider;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C2B (Paybill) confirmation callback - the Ratiba collection leg.
 * Public zone: always 200 (no retry storm from Safaricom); matching is
 * fail-closed in the service, never in this endpoint.
 */
@WebMvcTest(controllers = C2BConfirmationCallbackController.class)
@Import(C2BConfirmationCallbackControllerTest.PermitAllSecurityConfig.class)
class C2BConfirmationCallbackControllerTest {

    private static final String CONFIRMATION_URL =
            "/api/v1/public/subscription-billing/c2b/confirmation";

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
    private SubscriptionC2bPaymentService subscriptionC2bPaymentService;

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
        reset(subscriptionC2bPaymentService);
    }

    @Test
    void validConfirmation_returns200_andForwardsToService() throws Exception {
        mockMvc.perform(post(CONFIRMATION_URL)
                        .contentType("application/json")
                        .content("""
                                {
                                  "TransID": "RKTQ100",
                                  "TransAmount": "2500",
                                  "BillRefNumber": "T-001",
                                  "MSISDN": "254712345678",
                                  "BusinessShortCode": "174379"
                                }
                                """))
                .andExpect(status().isOk());

        verify(subscriptionC2bPaymentService).handleConfirmation(any());
    }

    @Test
    void serviceRejectsPayload_stillReturns200() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(subscriptionC2bPaymentService).handleConfirmation(any());

        mockMvc.perform(post(CONFIRMATION_URL)
                        .contentType("application/json")
                        .content("""
                                {
                                  "TransID": "RKTQ101",
                                  "TransAmount": "1.50",
                                  "BillRefNumber": "UNKNOWN",
                                  "MSISDN": "254700000000",
                                  "BusinessShortCode": "174379"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void emptyBody_returns200_serviceSkipsGracefully() throws Exception {
        mockMvc.perform(post(CONFIRMATION_URL)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());

        verify(subscriptionC2bPaymentService).handleConfirmation(any());
    }
}
