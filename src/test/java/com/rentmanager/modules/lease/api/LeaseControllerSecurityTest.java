package com.rentmanager.modules.lease.api;

import com.rentmanager.modules.lease.api.controller.LeaseController;
import com.rentmanager.modules.lease.application.dto.request.BillingCycleDTO;
import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;
import com.rentmanager.modules.lease.application.dto.request.LeaseTypeDTO;
import com.rentmanager.modules.lease.application.dto.response.LeaseActionResponse;
import com.rentmanager.modules.lease.application.dto.response.LeaseDetailResponse;
import com.rentmanager.modules.lease.application.dto.response.LeaseResponse;
import com.rentmanager.modules.lease.application.service.LeaseApplicationService;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.context.TenantContext;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC security tests for LeaseController.
 *
 * Coverage (per Handoff Addendum 5, §5 Step 3):
 *  - create        -> OWNER+MANAGER succeed, STAFF 403
 *  - update        -> OWNER+MANAGER succeed, STAFF 403
 *  - getById       -> OWNER+MANAGER+STAFF all succeed
 *  - executeAction -> OWNER+MANAGER succeed, STAFF 403
 *      NOTE: this endpoint bundles six transitions behind one @PreAuthorize;
 *      TERMINATE is not gated more strictly than RENEW/APPROVE. This is a
 *      known, deliberate limitation (Addendum 4 §4.2 / Addendum 5 §2.3) and
 *      is NOT something this test attempts to fix. APPROVE is used as the
 *      representative action for the success-path assertions since it needs
 *      no reason/terminationType per LeaseActionRequest's own conditional
 *      validators.
 *  - delete        -> OWNER succeeds ONLY. MANAGER gets its own explicit 403
 *      assertion, separate from STAFF's, per Addendum 4 §3.3 /
 *      Addendum 5 §5 Step 3 ("MANAGER needs its own explicit assertion here
 *      since it's not uniformly grouped with STAFF across this controller's
 *      other endpoints").
 *
 * KNOWN FLAGGED ISSUE (not fixed here, out of RBAC scope): LeaseTypeDTO and
 * the domain LeaseType enum are not the same set (LeaseTypeDTO has MONTHLY,
 * which has no corresponding domain LeaseType constant; the domain STANDARD
 * and MONTH_TO_MONTH constants are unreachable via the API). Request bodies
 * in this test deliberately use FIXED_TERM, the one value safe in both
 * enums, so this pre-existing bug does not interfere with RBAC assertions.
 *
 * CONFIRMED: TenantContext.setTenantId(UUID) / .clear() -- verified against
 * the real TenantContext.java source (this session). Not an assumption.
 *
 * CONFIRMED, CORRECTED THIS ROUND: authentication mechanism and method-
 * security wiring below now mirror PropertyCommandControllerSecurityTest.java
 * exactly, having seen its real source:
 *
 *  1. @WebMvcTest does NOT load arbitrary @Configuration classes, so
 *     @PreAuthorize was never being evaluated in the first version of this
 *     file -- every authenticated role passed every endpoint regardless of
 *     annotation. Fixed by adding the nested
 *     @TestConfiguration @EnableMethodSecurity static class below, exactly
 *     as PropertyCommandControllerSecurityTest.java does.
 *  2. Authentication is built as a ClerkAuthenticationToken wrapping a real
 *     (locally-constructed, unsigned) Jwt + AuthenticatedUser principal +
 *     authorities -- not a plain UsernamePasswordAuthenticationToken as
 *     originally guessed. Mirrors tokenWithAuthority() from
 *     PropertyCommandControllerSecurityTest.java, including the extra
 *     "ROLE_LANDLORD" base authority granted alongside the specific role.
 *  3. SecurityContextService's real package is
 *     com.rentmanager.shared.security.context, not .service as originally
 *     guessed -- this was the original "cannot resolve symbol" compile
 *     error's root cause.
 *
 * PITFALL FOUND AND FIXED (read before writing any further controller
 * security tests, same spirit as Addendum 5 §3's mockEntry() write-up):
 *
 * First real run: Tests run 15, Failures 7 -- every OWNER/MANAGER
 * "succeeds" test on create/update/executeAction/delete failed with
 * "expected:<200> but was:<403>". Root cause: CsrfFilter (confirmed active
 * in the printed filter chain) rejects any POST/PUT/DELETE without a valid
 * CSRF token, before @PreAuthorize is ever evaluated. GET is CSRF-exempt,
 * which is why getById was unaffected. Fixed via .with(csrf()) on every
 * mutating request.
 *
 * Second real run, after the csrf() fix: Tests run 15, Failures 5 -- this
 * time every STAFF-forbidden / MANAGER-forbidden-on-delete test failed with
 * "expected:<403> but was:<200>". This is the @EnableMethodSecurity gap
 * described above: with CSRF cleared but method security never wired into
 * the slice, @PreAuthorize was a no-op and every authenticated caller
 * reached the controller method regardless of role. Fixed by items 1-3
 * above, sourced directly from PropertyCommandControllerSecurityTest.java
 * rather than guessed a third time.
 */
@WebMvcTest(LeaseController.class)
class LeaseControllerSecurityTest {

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaseApplicationService leaseApplicationService;

    // Slice-wide infrastructure mocks required to start the WebMvcTest
    // context in this codebase -- see Addendum 5 §4.2.
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
    private static final UUID LEASE_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final UUID TENANT_PROFILE_ID = UUID.randomUUID();

    @BeforeEach
    void setUpTenantContext() {
        // CONFIRMED against real TenantContext.java this session.
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Mirrors PropertyCommandControllerSecurityTest.tokenWithAuthority(...)
     * exactly, having seen its real source this session.
     */
    private AbstractAuthenticationToken tokenWithAuthority(String authority) {
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

    private String createRequestJson() {
        return """
                {
                  "propertyId": "%s",
                  "unitId": "%s",
                  "tenantProfileId": "%s",
                  "leaseNumber": "LEASE-001",
                  "leaseType": "FIXED_TERM",
                  "billingCycle": "MONTHLY",
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31",
                  "rentAmount": 1000.00,
                  "securityDeposit": 500.00,
                  "lateFeeAmount": 50.00,
                  "gracePeriodDays": 5,
                  "autoRenew": false
                }
                """.formatted(PROPERTY_ID, UNIT_ID, TENANT_PROFILE_ID);
    }

    private String updateRequestJson() {
        return """
                {
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31",
                  "rentAmount": 1200.00,
                  "securityDeposit": 600.00,
                  "lateFeeAmount": 60.00,
                  "gracePeriodDays": 7,
                  "autoRenew": true
                }
                """;
    }

    private String actionRequestJson() {
        // APPROVE chosen deliberately: it needs neither `reason` nor
        // `terminationType`, unlike REJECT/TERMINATE (LeaseActionRequest's
        // own @AssertTrue validators), keeping this test's request body
        // minimal and stable regardless of action-specific validation.
        return """
                {
                  "performedBy": "%s",
                  "action": "APPROVE",
                  "actionDate": "2026-07-11"
                }
                """.formatted(UUID.randomUUID());
    }

    private LeaseResponse mockLeaseResponse() {
        return new LeaseResponse(
                LEASE_ID,
                "LEASE-001",
                PROPERTY_ID,
                UNIT_ID,
                TENANT_PROFILE_ID,
                LeaseType.FIXED_TERM,
                BillingCycleDTO.MONTHLY,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                new BigDecimal("50.00"),
                5,
                false,
                "DRAFT",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }





    private LeaseDetailResponse mockLeaseDetailResponse() {
        // UPDATED: LeaseDetailResponse extended this session with 8 new
        // lifecycle-metadata fields (signedAt through terminationReason).
        // All null here -- same reasoning as LeaseControllerRbacTest's
        // dummyDetailResponse(): this fixture is an ACTIVE lease with no
        // lifecycle events in its own mock timeline.
        return new LeaseDetailResponse(
                LEASE_ID,
                "LEASE-001",
                TENANT_ID,
                PROPERTY_ID,
                UNIT_ID,
                LeaseTypeDTO.FIXED_TERM,
                BillingCycleDTO.MONTHLY,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                new BigDecimal("1000.00"),
                new BigDecimal("500.00"),
                LeaseStatusDTO.ACTIVE,
                5,
                false,
                LocalDate.now(),
                LocalDate.now(),
                1L,
                null,  // tenantFullName
                null,  // tenantPhone
                null,  // signedAt
                null,  // activatedAt
                null,  // terminatedAt
                null,  // expiredAt
                null,  // renewedAt
                null,  // cancelledAt
                null,  // terminationType
                null   // terminationReason
        );
    }







    private LeaseActionResponse mockLeaseActionResponse() {
        return new LeaseActionResponse(
                LEASE_ID,
                LeaseStatusDTO.PENDING_APPROVAL,
                LeaseStatusDTO.ACTIVE,
                "APPROVE"
        );
    }

    // =====================================================================
    // create -- OWNER+MANAGER succeed, STAFF 403
    // =====================================================================

    @Test
    void create_owner_succeeds() throws Exception {
        // Build stub before opening the when() chain -- Addendum 5 §3.
        LeaseResponse response = mockLeaseResponse();
        when(leaseApplicationService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/leases")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void create_manager_succeeds() throws Exception {
        LeaseResponse response = mockLeaseResponse();
        when(leaseApplicationService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/leases")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_MANAGER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void create_staff_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/leases")
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // update -- OWNER+MANAGER succeed, STAFF 403
    // =====================================================================

    @Test
    void update_owner_succeeds() throws Exception {
        LeaseResponse response = mockLeaseResponse();
        when(leaseApplicationService.update(any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void update_manager_succeeds() throws Exception {
        LeaseResponse response = mockLeaseResponse();
        when(leaseApplicationService.update(any(), any())).thenReturn(response);

        mockMvc.perform(put("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_MANAGER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void update_staff_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequestJson()))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // getById -- OWNER+MANAGER+STAFF all succeed
    // =====================================================================

    @Test
    void getById_owner_succeeds() throws Exception {
        LeaseDetailResponse response = mockLeaseDetailResponse();
        when(leaseApplicationService.getById(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER"))))
                .andExpect(status().isOk());
    }

    @Test
    void getById_manager_succeeds() throws Exception {
        LeaseDetailResponse response = mockLeaseDetailResponse();
        when(leaseApplicationService.getById(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_MANAGER"))))
                .andExpect(status().isOk());
    }

    @Test
    void getById_staff_succeeds() throws Exception {
        LeaseDetailResponse response = mockLeaseDetailResponse();
        when(leaseApplicationService.getById(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF"))))
                .andExpect(status().isOk());
    }

    // =====================================================================
    // executeAction -- OWNER+MANAGER succeed, STAFF 403
    // =====================================================================

    @Test
    void executeAction_owner_succeeds() throws Exception {
        LeaseActionResponse response = mockLeaseActionResponse();
        when(leaseApplicationService.executeAction(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/leases/{leaseId}/action", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void executeAction_manager_succeeds() throws Exception {
        LeaseActionResponse response = mockLeaseActionResponse();
        when(leaseApplicationService.executeAction(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/leases/{leaseId}/action", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_MANAGER")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    void executeAction_staff_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/leases/{leaseId}/action", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(actionRequestJson()))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // delete -- OWNER succeeds ONLY. MANAGER and STAFF each get their own
    // explicit 403 assertion (Addendum 4 §3.3): MANAGER is deliberately
    // NOT grouped with STAFF here, since this is the one endpoint across
    // all four controllers where MANAGER is excluded from a tier it is
    // normally part of.
    // =====================================================================

    @Test
    void delete_owner_succeeds() throws Exception {
        doNothing().when(leaseApplicationService).delete(any());

        mockMvc.perform(delete("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_OWNER")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void delete_manager_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_MANAGER")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_staff_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/leases/{leaseId}", LEASE_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}