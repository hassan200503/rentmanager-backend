package com.rentmanager.modules.rentledger.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentmanager.modules.rentledger.api.controller.RentLedgerCommandController;
import com.rentmanager.modules.rentledger.api.dto.request.ApplyAdjustmentRequest;
import com.rentmanager.modules.rentledger.api.dto.request.RecordRentTransactionRequest;
import com.rentmanager.modules.rentledger.api.dto.request.ResolveOverpaymentCreditRequest;
import com.rentmanager.modules.rentledger.api.dto.request.ResolveOverpaymentRefundRequest;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionSource;
import com.rentmanager.modules.rentledger.domain.enums.RentTransactionType;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import com.rentmanager.modules.user.domain.repository.UserRepository;
import com.rentmanager.shared.error.ErrorTrackingService;
import com.rentmanager.shared.security.context.SecurityContextService;
import com.rentmanager.shared.security.jwt.ClerkAuthenticationToken;
import com.rentmanager.shared.security.jwt.JwtProvider;
import com.rentmanager.shared.security.principal.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers Addendum 4 §1.1/Q3 acceptance criteria for RentLedgerCommandController:
 *  - recordTransaction: OWNER, MANAGER, STAFF all succeed (ordinary rent
 *    collection tier — deliberately as permissive as Unit's
 *    markOccupied/markVacant, per the RBAC design rationale).
 *  - applyAdjustment / resolveOverpaymentWithRefund / resolveOverpaymentAsCredit:
 *    OWNER + MANAGER succeed, STAFF -> 403 (money-moving tier).
 *
 * DEVIATION FROM THE TenantControllerSecurityTest TEMPLATE (Addendum 4 §3.1),
 * NOTED EXPLICITLY PER THE TEMPLATE'S OWN INSTRUCTION NOT TO COPY BLINDLY:
 * RentLedgerCommandController.requireTenantId() reads tenantId from
 * AuthenticatedUser.getTenantId() directly (confirmed from the controller's
 * own pasted source), NOT from the TenantContext ThreadLocal. So unlike
 * TenantControllerSecurityTest, this class deliberately does NOT set/clear
 * TenantContext in @BeforeEach/@AfterEach -- doing so would be copying a
 * mechanism this controller doesn't use.
 *
 * @MockBean SET: RentLedgerCommandController's only real constructor
 * dependency is RentLedgerApplicationService. The remaining six mocks
 * (ErrorTrackingService, JwtProvider, SecurityContextService,
 * UserRepository, TenantRepository, TenantProfileRepository) are the same
 * global-infrastructure beans required to start ANY @WebMvcTest slice in
 * this codebase (GlobalExceptionHandler + the JWT filter chain +
 * ClerkJwtAuthenticationConverter), per TenantControllerSecurityTest --
 * carried over because they are slice-wide infrastructure, not because
 * RentLedgerCommandController itself depends on them.
 *
 * PREVIOUSLY-FLAGGED BLOCKER, NOW RESOLVED: recordTransaction /
 * applyAdjustment / resolveOverpaymentWithRefund all return RentLedgerEntry,
 * which the controller converts via RentLedgerEntryResponse.from(entry).
 * Traced against the real RentLedgerEntry.java / RentLedgerEntryResponse.java:
 * every field .from() reads is either stored directly from a getter
 * (fine on an unstubbed mock -- returns null/false, never dereferenced) or
 * computed via RentLedgerEntry's own getBalanceOwed()/getExcessAmount()
 * (also fine -- these are real method bodies on the class, but a class
 * mock intercepts the call and returns the default rather than executing
 * the body, so the internal BigDecimal arithmetic never runs against a
 * null amountDue/amountPaid). The ONE real landmine is
 * entry.getStatus().name() -- getStatus() unstubbed returns null on an
 * object-returning getter, and .name() on that null throws NPE. mockEntry()
 * below stubs getStatus() to RentLedgerStatus.DUE (a confirmed real
 * constant from the domain class) specifically to cover that one call;
 * nothing else needed stubbing.
 *
 * FIXED, REAL BUG FROM FIRST RUN (mvn test output pasted 2026-07-11):
 * 7 errors, all UnfinishedStubbingException. Root cause was NOT the
 * RentLedgerEntry/Response structure -- it was calling mockEntry() inline
 * as the argument to .thenReturn(...), e.g.
 * `when(service.applyTransaction(...)).thenReturn(mockEntry())`.
 * mockEntry() itself opens a nested when(entry.getStatus())...thenReturn()
 * call, which fires while the OUTER when(...) stubbing is still open
 * (Mockito hasn't reached the outer .thenReturn() yet, since it's still
 * evaluating that call's argument expression) -- corrupting Mockito's
 * global stubbing state. Fixed by building the mock into a local variable
 * BEFORE opening the when() chain in all three affected tests. Apply the
 * same ordering when writing PropertyCommandControllerSecurityTest /
 * UnitCommandControllerSecurityTest / LeaseControllerSecurityTest if any
 * of their stubs also return a value built by a helper that itself stubs
 * a mock.
 */
@WebMvcTest(controllers = RentLedgerCommandController.class)
class RentLedgerCommandControllerSecurityTest {

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
    private RentLedgerApplicationService rentLedgerApplicationService;

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
    private static final UUID TARGET_ENTRY_ID = UUID.randomUUID();

    // No TenantContext @BeforeEach/@AfterEach here -- see class javadoc.

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

    // --- Request body builders ---
    // NOTE on enum literals used: RentTransactionType.PAYMENT is directly
    // named in RentLedgerApplicationService.applyTransaction's own javadoc
    // as a valid type for this call path -- not a guess.
    // RentTransactionSource.ADMIN_ADJUSTMENT is a confirmed real constant
    // (used directly in RentLedgerApplicationService), chosen here ONLY to
    // satisfy @NotNull deserialization on the client-supplied `source`
    // field -- it is not a claim that ADMIN_ADJUSTMENT is what a real
    // caretaker-recorded payment would send. If the real enum turns out to
    // have a more semantically correct client-facing constant (e.g. a CASH
    // or MPESA value), swap it in once RentTransactionSource.java is seen --
    // it makes no difference to what these tests are actually verifying
    // (the @PreAuthorize gate), only to realism of the fixture.

    private String validTransactionRequestJson() throws Exception {
        RecordRentTransactionRequest request = new RecordRentTransactionRequest(
                RentTransactionType.PAYMENT,
                new BigDecimal("1500.00"),
                null,
                RentTransactionSource.ADMIN_ADJUSTMENT,
                null
        );
        return objectMapper.writeValueAsString(request);
    }

    private String validAdjustmentRequestJson() throws Exception {
        ApplyAdjustmentRequest request = new ApplyAdjustmentRequest(
                new BigDecimal("-200.00"),
                null
        );
        return objectMapper.writeValueAsString(request);
    }

    private String validRefundRequestJson() throws Exception {
        ResolveOverpaymentRefundRequest request = new ResolveOverpaymentRefundRequest(
                new BigDecimal("500.00"),
                null,
                RentTransactionSource.ADMIN_ADJUSTMENT,
                null
        );
        return objectMapper.writeValueAsString(request);
    }

    private String validCreditRequestJson() throws Exception {
        ResolveOverpaymentCreditRequest request = new ResolveOverpaymentCreditRequest(
                TARGET_ENTRY_ID,
                null
        );
        return objectMapper.writeValueAsString(request);
    }

    // Bare mock except for getStatus() -- see class javadoc for why that's
    // the only stub RentLedgerEntryResponse.from() actually requires.
    private RentLedgerEntry mockEntry() {
        RentLedgerEntry entry = org.mockito.Mockito.mock(RentLedgerEntry.class);
        when(entry.getStatus()).thenReturn(RentLedgerStatus.DUE);
        return entry;
    }

    // ================= recordTransaction: OWNER, MANAGER, STAFF all succeed =================

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER", "ROLE_LANDLORD_STAFF"})
    void allLandlordRoles_succeed_onRecordTransaction(String authority) throws Exception {
        RentLedgerEntry entry = mockEntry();
        // The controller calls the idempotency-key overload (V98); the key is
        // null here because this test sends no Idempotency-Key header.
        when(rentLedgerApplicationService.applyTransaction(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), isNull()))
                .thenReturn(entry);

        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/transactions", ENTRY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validTransactionRequestJson()))
                .andExpect(status().isOk());
    }

    // ================= applyAdjustment: STAFF forbidden, OWNER/MANAGER succeed =================

    @Test
    void staff_forbidden_onApplyAdjustment() throws Exception {
        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/adjustments", ENTRY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validAdjustmentRequestJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onApplyAdjustment(String authority) throws Exception {
        RentLedgerEntry entry = mockEntry();
        when(rentLedgerApplicationService.applyAdjustment(
                any(), any(), any(), any(), any(), any()))
                .thenReturn(entry);

        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/adjustments", ENTRY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validAdjustmentRequestJson()))
                .andExpect(status().isOk());
    }

    // ============ resolveOverpaymentWithRefund: STAFF forbidden, OWNER/MANAGER succeed ============

    @Test
    void staff_forbidden_onResolveOverpaymentWithRefund() throws Exception {
        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/overpayment/refund", ENTRY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRefundRequestJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onResolveOverpaymentWithRefund(String authority) throws Exception {
        RentLedgerEntry entry = mockEntry();
        when(rentLedgerApplicationService.resolveOverpaymentWithRefund(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(entry);

        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{entryId}/overpayment/refund", ENTRY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validRefundRequestJson()))
                .andExpect(status().isOk());
    }

    // ============= resolveOverpaymentAsCredit: STAFF forbidden, OWNER/MANAGER succeed =============
    // Safe regardless of the RentLedgerEntry blocker -- the endpoint returns void.

    @Test
    void staff_forbidden_onResolveOverpaymentAsCredit() throws Exception {
        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{sourceEntryId}/overpayment/credit", ENTRY_ID)
                        .with(authentication(tokenWithAuthority("ROLE_LANDLORD_STAFF")))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validCreditRequestJson()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_LANDLORD_OWNER", "ROLE_LANDLORD_MANAGER"})
    void ownerAndManager_succeed_onResolveOverpaymentAsCredit(String authority) throws Exception {
        mockMvc.perform(post(RENT_LEDGER_BASE + "/entries/{sourceEntryId}/overpayment/credit", ENTRY_ID)
                        .with(authentication(tokenWithAuthority(authority)))
                        .with(csrf())
                        .contentType("application/json")
                        .content(validCreditRequestJson()))
                .andExpect(status().isOk());
    }
}