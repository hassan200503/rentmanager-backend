package com.rentmanager.modules.tenant.api;

import com.rentmanager.modules.audit.application.service.FinancialAuditService;
import com.rentmanager.modules.tenant.api.controller.PayoutDestinationController;
import com.rentmanager.modules.tenant.domain.model.Tenant;
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

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The payout destination decides where every shilling this platform sends a
 * landlord ends up, so the write is OWNER-only and every change is audited.
 *
 * <p>MANAGER can raise a disbursement but must not be able to change where
 * disbursements go. Allowing both would undo the point of deriving the
 * recipient server-side: a manager who can edit the destination and then
 * request a payout has exactly the capability the derivation removed.
 */
@WebMvcTest(controllers = PayoutDestinationController.class)
class PayoutDestinationControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private FinancialAuditService financialAuditService;

    @MockBean
    private ErrorTrackingService errorTrackingService;

    @MockBean
    private JwtProvider jwtProvider;

    @MockBean
    private SecurityContextService securityContextService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private TenantProfileRepository tenantProfileRepository;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String NEW_NUMBER = "+254711000111";

    private AbstractAuthenticationToken token(String authority, UUID tenantId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .claim("sub", "clerk_user_id")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        Set<? extends GrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority(authority));
        AuthenticatedUser principal = new AuthenticatedUser(
                USER_ID, tenantId, "landlord@example.com", "", true, authorities);

        return new ClerkAuthenticationToken(principal, jwt, authorities);
    }

    private void landlordExists(String currentPayoutNumber) {
        Tenant landlord = mock(Tenant.class);
        when(landlord.getPayoutPhoneNumber()).thenReturn(currentPayoutNumber);
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(landlord));
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private String body(String number) {
        return "{\"payoutPhoneNumber\": \"" + number + "\"}";
    }

    // ── Write: OWNER only ────────────────────────────────────────────────

    @Test
    void owner_canChangeTheDestination() throws Exception {
        landlordExists("+254700000000");

        mockMvc.perform(put("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NEW_NUMBER)))
                .andExpect(status().isOk());
    }

    /**
     * The separation that makes server-side recipient derivation worth having.
     */
    @Test
    void manager_cannotChangeTheDestination() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_LANDLORD_MANAGER", TENANT_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NEW_NUMBER)))
                .andExpect(status().isForbidden());

        verify(tenantRepository, never()).save(any());
    }

    @Test
    void staff_cannotChangeTheDestination() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_LANDLORD_STAFF", TENANT_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NEW_NUMBER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void renter_cannotChangeTheDestination() throws Exception {
        mockMvc.perform(put("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_TENANT", null)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NEW_NUMBER)))
                .andExpect(status().isForbidden());
    }

    // ── Auditing ─────────────────────────────────────────────────────────

    /**
     * Account-takeover fraud in payment systems begins here, which makes this
     * the highest-value row in the audit table.
     */
    @Test
    void everyChangeIsAudited() throws Exception {
        landlordExists("+254700000000");

        mockMvc.perform(put("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NEW_NUMBER)))
                .andExpect(status().isOk());

        verify(financialAuditService).payoutDestinationChanged(eq(TENANT_ID), any(), any());
    }

    @Test
    void settingItForTheFirstTimeRecordsThatTherePreviouslyWasNone() throws Exception {
        landlordExists(null);

        mockMvc.perform(put("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(NEW_NUMBER)))
                .andExpect(status().isOk());

        verify(financialAuditService).payoutDestinationChanged(eq(TENANT_ID), eq("none"), any());
    }

    // ── Validation ───────────────────────────────────────────────────────

    @Test
    void aMalformedNumberIsRejectedBeforeItCanBreakAPayout() throws Exception {
        for (String bad : new String[]{"0712345678", "+2541123456789", "+254712345", "not-a-number", ""}) {
            mockMvc.perform(put("/api/v1/tenants/payout-destination")
                            .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID)))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(bad)))
                    .andExpect(status().isBadRequest());
        }

        verify(tenantRepository, never()).save(any());
    }

    // ── Read: never returns the full number ──────────────────────────────

    /**
     * A read-only token leak should not also hand over the payout number —
     * that turns an information disclosure into the first step of redirecting
     * the money.
     */
    @Test
    void theReadEndpointNeverReturnsTheFullNumber() throws Exception {
        landlordExists("+254712345678");

        mockMvc.perform(get("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.maskedPhoneNumber").value(org.hamcrest.Matchers.not("+254712345678")))
                .andExpect(jsonPath("$.data.maskedPhoneNumber").value(org.hamcrest.Matchers.containsString("*")));
    }

    @Test
    void anUnsetDestinationReportsItselfAsUnconfigured() throws Exception {
        landlordExists(null);

        mockMvc.perform(get("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false));
    }

    @Test
    void manager_mayReadEvenThoughTheyCannotWrite() throws Exception {
        landlordExists("+254712345678");

        mockMvc.perform(get("/api/v1/tenants/payout-destination")
                        .with(authentication(token("ROLE_LANDLORD_MANAGER", TENANT_ID))))
                .andExpect(status().isOk());
    }
}
