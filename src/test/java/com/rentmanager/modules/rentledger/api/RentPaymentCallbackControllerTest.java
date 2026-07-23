package com.rentmanager.modules.rentledger.api;

import com.rentmanager.modules.rentledger.api.controller.RentPaymentCallbackController;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentCallbackService;
import com.rentmanager.modules.reservation.infrastructure.daraja.DarajaProperties;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RentPaymentCallbackController.class)
@Import(RentPaymentCallbackControllerTest.PermitAllSecurityConfig.class)
class RentPaymentCallbackControllerTest {

    private static final String CALLBACK_BASE = "/api/v1/public/rent-ledger/mpesa/callback";
    private static final String VALID_SECRET = "test-secret-value";

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
    private RentPaymentCallbackService rentPaymentCallbackService;

    @MockBean
    private DarajaProperties darajaProperties;

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
        reset(darajaProperties, rentPaymentCallbackService);
    }

    @Nested
    class SecretGating {

        @Test
        void correctSecret_callsServiceAndReturns200() throws Exception {
            when(darajaProperties.getCallbackSecret()).thenReturn(VALID_SECRET);

            mockMvc.perform(post(CALLBACK_BASE + "/{secret}", VALID_SECRET)
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isOk());

            verify(rentPaymentCallbackService).handle(any());
        }

        @Test
        void wrongSecret_returns404_andDoesNotCallService() throws Exception {
            when(darajaProperties.getCallbackSecret()).thenReturn(VALID_SECRET);

            mockMvc.perform(post(CALLBACK_BASE + "/{secret}", "wrong-secret")
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isNotFound());

            verify(rentPaymentCallbackService, never()).handle(any());
        }

        @Test
        void nullCallbackSecretInConfig_returns404() throws Exception {
            when(darajaProperties.getCallbackSecret()).thenReturn(null);

            mockMvc.perform(post(CALLBACK_BASE + "/{secret}", VALID_SECRET)
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isNotFound());

            verify(rentPaymentCallbackService, never()).handle(any());
        }

        @Test
        void blankCallbackSecretInConfig_returns404() throws Exception {
            when(darajaProperties.getCallbackSecret()).thenReturn("   ");

            mockMvc.perform(post(CALLBACK_BASE + "/{secret}", VALID_SECRET)
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isNotFound());

            verify(rentPaymentCallbackService, never()).handle(any());
        }
    }
}
