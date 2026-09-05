package com.rentmanager.modules.rentledger.api;

import com.rentmanager.modules.rentledger.api.controller.RentLedgerQueryController;
import com.rentmanager.modules.rentledger.application.dto.RentLedgerSummary;
import com.rentmanager.modules.rentledger.application.query.service.RentLedgerQueryService;
import com.rentmanager.modules.rentledger.application.service.RentLedgerSummaryService;
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
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC and tenant-scoping for {@code GET /api/v1/rent-ledger/summary}.
 *
 * <p>This endpoint returns portfolio-wide money figures, so two properties
 * matter and neither can be assumed: only landlord-side roles may call it,
 * and the tenant it reports on comes from the verified JWT rather than
 * anything the caller can influence.
 *
 * <p>STAFF is deliberately permitted, matching the rest of this query
 * controller — a caretaker who records payments needs to see what is still
 * owed, and nothing here exposes an individual renter.
 */
@WebMvcTest(controllers = RentLedgerQueryController.class)
class RentLedgerSummaryControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RentLedgerQueryService rentLedgerQueryService;

    @MockBean
    private RentLedgerSummaryService rentLedgerSummaryService;

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
    private static final UUID TENANT_ID = UUID.randomUUID();

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

    private RentLedgerSummary summary() {
        return new RentLedgerSummary(
                new BigDecimal("12000.00"), new BigDecimal("8000.00"),
                new BigDecimal("3000.00"), 2L, "KES");
    }

    @Test
    void owner_canReadTheSummary() throws Exception {
        when(rentLedgerSummaryService.getSummary(any())).thenReturn(summary());

        mockMvc.perform(get("/api/v1/rent-ledger/summary")
                        .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID))))
                .andExpect(status().isOk());
    }

    @Test
    void manager_canReadTheSummary() throws Exception {
        when(rentLedgerSummaryService.getSummary(any())).thenReturn(summary());

        mockMvc.perform(get("/api/v1/rent-ledger/summary")
                        .with(authentication(token("ROLE_LANDLORD_MANAGER", TENANT_ID))))
                .andExpect(status().isOk());
    }

    @Test
    void staff_canReadTheSummary_becauseACaretakerNeedsToKnowWhatIsOwed() throws Exception {
        when(rentLedgerSummaryService.getSummary(any())).thenReturn(summary());

        mockMvc.perform(get("/api/v1/rent-ledger/summary")
                        .with(authentication(token("ROLE_LANDLORD_STAFF", TENANT_ID))))
                .andExpect(status().isOk());
    }

    /**
     * A renter must never see portfolio-wide landlord revenue.
     */
    @Test
    void renter_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/rent-ledger/summary")
                        .with(authentication(token("ROLE_TENANT", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingOnboarding_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/rent-ledger/summary")
                        .with(authentication(token("ROLE_PENDING_ONBOARDING", null))))
                .andExpect(status().isForbidden());
    }

    /**
     * The tenant reported on is taken from the authenticated principal. There
     * is no path variable, header or query parameter a caller could use to ask
     * for another landlord's figures — asserted here so that adding one later
     * fails this test rather than shipping.
     */
    @Test
    void theSummaryIsScopedToTheAuthenticatedTenantOnly() throws Exception {
        when(rentLedgerSummaryService.getSummary(any())).thenReturn(summary());

        mockMvc.perform(get("/api/v1/rent-ledger/summary")
                        .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID))))
                .andExpect(status().isOk());

        verify(rentLedgerSummaryService).getSummary(TENANT_ID);
    }

    /**
     * Money is serialised as a string, per the convention adopted when
     * backend defect #10 was closed. A JSON number becomes a double in the
     * browser, and "collected this month" is exactly the figure a landlord
     * would notice being a cent out.
     */
    @Test
    void moneyIsSerialisedAsStringsNotNumbers() throws Exception {
        when(rentLedgerSummaryService.getSummary(any())).thenReturn(summary());

        mockMvc.perform(get("/api/v1/rent-ledger/summary")
                        .with(authentication(token("ROLE_LANDLORD_OWNER", TENANT_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectedThisMonth").isString())
                .andExpect(jsonPath("$.data.outstandingTotal").isString())
                .andExpect(jsonPath("$.data.overdueTotal").isString())
                .andExpect(jsonPath("$.data.overdueEntryCount").isNumber());
    }
}
