package com.rentmanager.modules.rentledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.rentledger.api.controller.RentPaymentController;
import com.rentmanager.modules.rentledger.api.dto.request.InitiateRentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.enums.RentPaymentRequestStatus;
import com.rentmanager.modules.rentledger.domain.model.RentPaymentRequest;
import com.rentmanager.modules.rentledger.domain.repository.RentPaymentRequestRepository;
import com.rentmanager.modules.rentledger.infrastructure.daraja.RentPaymentInitiationService;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RentPaymentController.class)
class RentPaymentControllerTest {

    private static final String RENT_LEDGER_BASE = "/api/v1/rent-ledger";

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RentPaymentInitiationService rentPaymentInitiationService;

    @MockBean
    private RentPaymentRequestRepository rentPaymentRequestRepository;

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

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final UUID REQUEST_ID = UUID.randomUUID();

    private ClerkAuthenticationToken tokenWithAuthority(String authority) {
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
                TENANT_ID,
                "user@example.com",
                "",
                true,
                authorities
        );

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    private String collectRequestJson() throws Exception {
        return objectMapper.writeValueAsString(
                new InitiateRentPaymentRequest("+254712345678")
        );
    }

    // ================= collect: RBAC =================

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER", "ROLE_LANDLORD_STAFF"})
    void authorizedRoles_succeed_onCollect(String authority) throws Exception {
        when(rentPaymentInitiationService.initiate(any(), any(), anyString()))
                .thenReturn(mock(RentPaymentRequest.class));

        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/collect", ENTRY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(collectRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_TENANT", "ROLE_ANONYMOUS"})
    void unauthorizedRoles_getForbidden_onCollect(String authority) throws Exception {
        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/collect", ENTRY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(collectRequestJson()))
                .andExpect(status().isForbidden());
    }

    // ================= collect: validation =================

    @Test
    void missingMpesaPhone_returnsBadRequest() throws Exception {
        String invalidJson = """
                {"mpesaPhone": ""}
                """;

        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/collect", ENTRY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(rentPaymentInitiationService, never()).initiate(any(), any(), anyString());
    }

    @Test
    void invalidPhoneFormat_returnsBadRequest() throws Exception {
        String invalidJson = """
                {"mpesaPhone": "12345"}
                """;

        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/collect", ENTRY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(rentPaymentInitiationService, never()).initiate(any(), any(), anyString());
    }

    @Test
    void emptyBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/collect", ENTRY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(rentPaymentInitiationService, never()).initiate(any(), any(), anyString());
    }

    // ================= status endpoint =================

    @Nested
    class StatusEndpoint {

        private RentPaymentRequest pendingRequest;
        private RentPaymentRequest paidRequest;
        private RentPaymentRequest failedRequest;

        @BeforeEach
        void setUp() {
            UUID leaseId = UUID.randomUUID();

            pendingRequest = mock(RentPaymentRequest.class);
            when(pendingRequest.getId()).thenReturn(REQUEST_ID);
            when(pendingRequest.getLeaseId()).thenReturn(leaseId);
            when(pendingRequest.getRentLedgerEntryId()).thenReturn(ENTRY_ID);
            when(pendingRequest.getAmount()).thenReturn(new BigDecimal("1500.00"));
            when(pendingRequest.getStatus()).thenReturn(RentPaymentRequestStatus.PENDING);
            when(pendingRequest.getMpesaReceiptNumber()).thenReturn(null);

            paidRequest = mock(RentPaymentRequest.class);
            when(paidRequest.getId()).thenReturn(REQUEST_ID);
            when(paidRequest.getLeaseId()).thenReturn(leaseId);
            when(paidRequest.getRentLedgerEntryId()).thenReturn(ENTRY_ID);
            when(paidRequest.getAmount()).thenReturn(new BigDecimal("1500.00"));
            when(paidRequest.getStatus()).thenReturn(RentPaymentRequestStatus.PAID);
            when(paidRequest.getMpesaReceiptNumber()).thenReturn("NLJ7RT61SV");

            failedRequest = mock(RentPaymentRequest.class);
            when(failedRequest.getId()).thenReturn(REQUEST_ID);
            when(failedRequest.getLeaseId()).thenReturn(leaseId);
            when(failedRequest.getRentLedgerEntryId()).thenReturn(ENTRY_ID);
            when(failedRequest.getAmount()).thenReturn(new BigDecimal("1500.00"));
            when(failedRequest.getStatus()).thenReturn(RentPaymentRequestStatus.FAILED);
            when(failedRequest.getMpesaReceiptNumber()).thenReturn(null);
        }

        @Test
        void returnsPendingStatus() throws Exception {
            when(rentPaymentRequestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                    .thenReturn(Optional.of(pendingRequest));

            mockMvc.perform(get(RENT_LEDGER_BASE + "/rent-payment-requests/{id}/status", REQUEST_ID)
                            .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(REQUEST_ID.toString()))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.mpesaReceiptNumber").doesNotExist());
        }

        @Test
        void returnsPaidStatus() throws Exception {
            when(rentPaymentRequestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                    .thenReturn(Optional.of(paidRequest));

            mockMvc.perform(get(RENT_LEDGER_BASE + "/rent-payment-requests/{id}/status", REQUEST_ID)
                            .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("PAID"))
                    .andExpect(jsonPath("$.data.mpesaReceiptNumber").value("NLJ7RT61SV"));
        }

        @Test
        void returnsFailedStatus() throws Exception {
            when(rentPaymentRequestRepository.findByIdAndTenantId(REQUEST_ID, TENANT_ID))
                    .thenReturn(Optional.of(failedRequest));

            mockMvc.perform(get(RENT_LEDGER_BASE + "/rent-payment-requests/{id}/status", REQUEST_ID)
                            .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("FAILED"))
                    .andExpect(jsonPath("$.data.mpesaReceiptNumber").doesNotExist());
        }

        @Test
        void unknownId_returnsNotFound() throws Exception {
            UUID missingId = UUID.randomUUID();
            when(rentPaymentRequestRepository.findByIdAndTenantId(missingId, TENANT_ID))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get(RENT_LEDGER_BASE + "/rent-payment-requests/{id}/status", missingId)
                            .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }
}
