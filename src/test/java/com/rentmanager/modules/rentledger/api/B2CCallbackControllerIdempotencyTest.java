package com.rentmanager.modules.rentledger.api;

import com.rentmanager.modules.rentledger.api.controller.B2CCallbackController;
import com.rentmanager.modules.rentledger.application.service.B2CDisbursementService;
import com.rentmanager.modules.rentledger.infrastructure.daraja.DarajaB2CProperties;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@code B2CCallbackController}.
 *
 * Mirrors the existing {@code RentPaymentCallbackControllerTest} pattern:
 * {@code @WebMvcTest} with an inline {@code PermitAllSecurityConfig}.
 *
 * Covers:
 *   1. Secret gating: correct secret passes; wrong/null/blank secret → 404.
 *   2. Idempotency: the controller always returns 200 even when the service
 *      throws (Daraja requires 200 for all callbacks; a non-200 causes
 *      Safaricom to retry). The duplicate-callback guard in the service
 *      prevents double processing.
 *   3. Timeout callback: correct secret → service called; wrong secret → 404.
 *   4. Result extraction: success payload correctly routes result fields
 *      to the service call.
 */
@WebMvcTest(controllers = B2CCallbackController.class)
@Import(B2CCallbackControllerIdempotencyTest.PermitAllSecurityConfig.class)
class B2CCallbackControllerIdempotencyTest {

    private static final String BASE_URL = "/api/v1/public/disbursements/mpesa";
    private static final String VALID_SECRET = "test-b2c-callback-secret";

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
    private B2CDisbursementService b2cDisbursementService;

    @MockBean
    private DarajaB2CProperties b2cProperties;

    // Required by the Spring context even if not directly exercised by these tests
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
        reset(b2cProperties, b2cDisbursementService);
        when(b2cProperties.getCallbackSecret()).thenReturn(VALID_SECRET);
        when(b2cDisbursementService.handleResult(any(), any(), any(), any(), any()))
                .thenReturn(null); // return value unused by controller
        when(b2cDisbursementService.handleTimeout(any()))
                .thenReturn(null);
    }

    @Nested
    class SecretGating {

        @Test
        void correctSecret_resultEndpoint_returns200() throws Exception {
            mockMvc.perform(post(BASE_URL + "/result/{secret}/{id}", VALID_SECRET, UUID.randomUUID())
                            .contentType("application/json")
                            .content(buildSuccessPayload()))
                    .andExpect(status().isOk());

            verify(b2cDisbursementService).handleResult(any(), any(), any(), any(), any());
        }

        @Test
        void wrongSecret_resultEndpoint_returns404_doesNotCallService() throws Exception {
            mockMvc.perform(post(BASE_URL + "/result/{secret}/{id}", "wrong-secret", UUID.randomUUID())
                            .contentType("application/json")
                            .content(buildSuccessPayload()))
                    .andExpect(status().isNotFound());

            verify(b2cDisbursementService, never()).handleResult(any(), any(), any(), any(), any());
        }

        @Test
        void nullConfigSecret_resultEndpoint_returns404() throws Exception {
            when(b2cProperties.getCallbackSecret()).thenReturn(null);

            mockMvc.perform(post(BASE_URL + "/result/{secret}/{id}", VALID_SECRET, UUID.randomUUID())
                            .contentType("application/json")
                            .content(buildSuccessPayload()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void correctSecret_timeoutEndpoint_returns200() throws Exception {
            mockMvc.perform(post(BASE_URL + "/timeout/{secret}/{id}", VALID_SECRET, UUID.randomUUID())
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isOk());

            verify(b2cDisbursementService).handleTimeout(any());
        }

        @Test
        void wrongSecret_timeoutEndpoint_returns404_doesNotCallService() throws Exception {
            mockMvc.perform(post(BASE_URL + "/timeout/{secret}/{id}", "bad-secret", UUID.randomUUID())
                            .contentType("application/json")
                            .content("{}"))
                    .andExpect(status().isNotFound());

            verify(b2cDisbursementService, never()).handleTimeout(any());
        }
    }

    @Nested
    class Idempotency {

        /**
         * The controller must ALWAYS return 200 even when the service throws.
         * Daraja's documentation requires a 200 response for all callbacks;
         * any non-200 triggers a retry from Safaricom. The service-level
         * idempotency guard (checking for terminal status) prevents the
         * double-processing — the controller's job is only to absorb exceptions
         * and always acknowledge.
         */
        @Test
        void serviceException_doesNotCausNon200Response() throws Exception {
            when(b2cDisbursementService.handleResult(any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("Simulated processing error"));

            mockMvc.perform(post(BASE_URL + "/result/{secret}/{id}", VALID_SECRET, UUID.randomUUID())
                            .contentType("application/json")
                            .content(buildSuccessPayload()))
                    .andExpect(status().isOk()); // Must still be 200
        }

        /**
         * Simulates Daraja delivering the same success callback twice.
         * The second delivery must also return 200 (not 4xx/5xx).
         * The actual idempotency (not posting the REFUND twice) is proven in
         * B2CDisbursementServiceTest.IdempotentCallbacks.
         */
        @Test
        void duplicateSuccessCallback_bothReturn200() throws Exception {
            UUID disbursementId = UUID.randomUUID();

            mockMvc.perform(post(BASE_URL + "/result/{secret}/{id}", VALID_SECRET, disbursementId)
                            .contentType("application/json")
                            .content(buildSuccessPayload()))
                    .andExpect(status().isOk());

            mockMvc.perform(post(BASE_URL + "/result/{secret}/{id}", VALID_SECRET, disbursementId)
                            .contentType("application/json")
                            .content(buildSuccessPayload()))
                    .andExpect(status().isOk());

            verify(b2cDisbursementService, times(2)).handleResult(
                    eq(disbursementId), any(), any(), any(), any());
        }
    }

    @Nested
    class PayloadExtraction {

        @Test
        void successPayload_extractsResultCodeAndDescription() throws Exception {
            UUID disbursementId = UUID.randomUUID();

            mockMvc.perform(post(BASE_URL + "/result/{secret}/{id}", VALID_SECRET, disbursementId)
                            .contentType("application/json")
                            .content(buildSuccessPayload()))
                    .andExpect(status().isOk());

            verify(b2cDisbursementService).handleResult(
                    eq(disbursementId),
                    eq("0"),        // ResultCode
                    eq("Success"),  // ResultDesc
                    any(),          // TransactionID (from ResultParameters)
                    any()           // ConversationID
            );
        }

        @Test
        void failurePayload_extractsNonZeroResultCode() throws Exception {
            UUID disbursementId = UUID.randomUUID();

            mockMvc.perform(post(BASE_URL + "/result/{secret}/{id}", VALID_SECRET, disbursementId)
                            .contentType("application/json")
                            .content(buildFailurePayload()))
                    .andExpect(status().isOk());

            verify(b2cDisbursementService).handleResult(
                    eq(disbursementId),
                    eq("1"),                    // ResultCode
                    eq("Insufficient funds"),   // ResultDesc
                    isNull(),                   // No TransactionID on failure
                    any()
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Payload builders
    // ─────────────────────────────────────────────────────────────────────

    private String buildSuccessPayload() {
        return """
                {
                  "ResultCode": "0",
                  "ResultDesc": "Success",
                  "ConversationID": "conv-test-123",
                  "ResultParameters": {
                    "TransactionID": "TXN12345678"
                  }
                }
                """;
    }

    private String buildFailurePayload() {
        return """
                {
                  "ResultCode": "1",
                  "ResultDesc": "Insufficient funds",
                  "ConversationID": "conv-fail-456"
                }
                """;
    }
}
