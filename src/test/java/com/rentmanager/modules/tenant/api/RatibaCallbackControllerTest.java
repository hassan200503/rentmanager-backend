package com.rentmanager.modules.tenant.api;

import com.rentmanager.modules.tenant.api.controller.RatibaCallbackController;
import com.rentmanager.modules.tenant.application.service.RatibaStandingOrderService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Async Ratiba creation callback (createStandingOrderExternal result).
 * Public zone with no secret path (URL registered per creation request) -
 * always 200 so Safaricom does not retry; unknown responseRefIDs are
 * logged-and-ignored in the service, never applied.
 */
@WebMvcTest(controllers = RatibaCallbackController.class)
@Import(RatibaCallbackControllerTest.PermitAllSecurityConfig.class)
class RatibaCallbackControllerTest {

    private static final String CALLBACK_URL =
            "/api/v1/public/subscription-billing/ratiba/callback";

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
    private RatibaStandingOrderService ratibaStandingOrderService;

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
        reset(ratibaStandingOrderService);
    }

    private static String creationCallbackJson(String status, String transactionId, String description) {
        return """
                {
                  "responseHeader": {
                    "responseRefID": "ref-123",
                    "responseDescription": "%s"
                  },
                  "responseBody": {
                    "responseData": [
                      { "name": "Status", "value": "%s" },
                      { "name": "TransactionID", "value": "%s" }
                    ]
                  }
                }
                """.formatted(description, status, transactionId);
    }

    @Test
    void successfulCreation_returns200_andResolvesOrderToActive() throws Exception {
        mockMvc.perform(post(CALLBACK_URL)
                        .contentType("application/json")
                        .content(creationCallbackJson("OKAY", "SO_ORD_1", "Success")))
                .andExpect(status().isOk());

        verify(ratibaStandingOrderService)
                .handleCreationCallback(eq("ref-123"), eq(true), eq("SO_ORD_1"), eq("Success"));
    }

    @Test
    void rejectedCreation_returns200_andResolvesOrderToFailed() throws Exception {
        mockMvc.perform(post(CALLBACK_URL)
                        .contentType("application/json")
                        .content(creationCallbackJson("FAILED", "", "Customer declined")))
                .andExpect(status().isOk());

        verify(ratibaStandingOrderService)
                .handleCreationCallback(eq("ref-123"), eq(false), eq(""), eq("Customer declined"));
    }

    @Test
    void missingResponseRefId_returns200_andIgnores() throws Exception {
        mockMvc.perform(post(CALLBACK_URL)
                        .contentType("application/json")
                        .content("""
                                {
                                  "responseBody": {
                                    "responseData": [
                                      { "name": "Status", "value": "OKAY" }
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        verify(ratibaStandingOrderService, never()).handleCreationCallback(
                any(), anyBoolean(), any(), any());
    }

    @Test
    void malformedJson_rejectedByDeserializerWithoutReachingService() throws Exception {
        mockMvc.perform(post(CALLBACK_URL)
                        .contentType("application/json")
                        .content("{not valid json"))
                .andExpect(status().isBadRequest());

        verify(ratibaStandingOrderService, never()).handleCreationCallback(
                any(), anyBoolean(), any(), any());
    }
}
