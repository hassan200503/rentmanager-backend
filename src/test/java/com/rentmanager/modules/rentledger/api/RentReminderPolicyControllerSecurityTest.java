package com.rentmanager.modules.rentledger.api;

import com.rentmanager.modules.rentledger.api.controller.RentReminderPolicyController;
import com.rentmanager.modules.rentledger.application.reminder.RentReminderPolicyService;
import com.rentmanager.modules.rentledger.domain.enums.ReminderMilestone;
import com.rentmanager.modules.rentledger.domain.model.RentReminderPolicy;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC coverage for the reminder cadence endpoints.
 *
 * <p>The cadence decides whether every tenant in a portfolio gets texted, and
 * SMS is billed to the landlord per message, so it sits at the same level as
 * any other setting that spends their money: OWNER and MANAGER only. STAFF is
 * deliberately excluded — a caretaker records payments (which is why
 * recording a payment is STAFF-accessible on purpose) but must not be able to
 * change what the whole portfolio receives.
 *
 * <p>Wiring mirrors {@code TenantPortalControllerSecurityTest}: the same
 * slice-wide mock beans and the same nested {@code @TestConfiguration
 * @EnableMethodSecurity}, without which {@code @PreAuthorize} is a no-op in a
 * {@code @WebMvcTest}.
 */
@WebMvcTest(controllers = RentReminderPolicyController.class)
class RentReminderPolicyControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RentReminderPolicyService policyService;

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

    private AbstractAuthenticationToken tokenWithAuthority(String authority, UUID tenantId) {
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

    private String cadenceJson() {
        return """
                {
                  "milestones": [
                    { "milestone": "DUE_TODAY", "enabled": true, "smsEnabled": false,
                      "emailEnabled": true, "whatsappEnabled": false, "notifyLandlord": false }
                  ]
                }
                """;
    }

    private List<RentReminderPolicy> cadence() {
        return List.of(RentReminderPolicy.defaultFor(TENANT_ID, ReminderMilestone.DUE_TODAY));
    }

    // ── GET /cadence ─────────────────────────────────────────────────────

    @Test
    void getCadence_owner_succeeds() throws Exception {
        when(policyService.getCadence(any())).thenReturn(cadence());

        mockMvc.perform(get("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER", TENANT_ID))))
                .andExpect(status().isOk());
    }

    @Test
    void getCadence_manager_succeeds() throws Exception {
        when(policyService.getCadence(any())).thenReturn(cadence());

        mockMvc.perform(get("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_MANAGER", TENANT_ID))))
                .andExpect(status().isOk());
    }

    @Test
    void getCadence_staff_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF", TENANT_ID))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCadence_renter_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_TENANT", null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCadence_pendingOnboarding_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_PENDING_ONBOARDING", null))))
                .andExpect(status().isForbidden());
    }

    // ── PUT /cadence ─────────────────────────────────────────────────────

    @Test
    void updateCadence_owner_succeeds() throws Exception {
        when(policyService.updateCadence(any(), anyList())).thenReturn(cadence());

        mockMvc.perform(put("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER", TENANT_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadenceJson()))
                .andExpect(status().isOk());
    }

    @Test
    void updateCadence_staff_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF", TENANT_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadenceJson()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCadence_renter_isForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_TENANT", null)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cadenceJson()))
                .andExpect(status().isForbidden());
    }

    /**
     * An empty milestone list would wipe the cadence back to defaults through
     * the "fill every unspecified milestone" path, which is not something a
     * landlord can have meant by pressing save on an empty grid.
     */
    @Test
    void updateCadence_emptyMilestoneList_isRejected() throws Exception {
        mockMvc.perform(put("/api/v1/rent-reminders/cadence")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER", TENANT_ID)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"milestones\": []}"))
                .andExpect(status().isBadRequest());
    }
}
