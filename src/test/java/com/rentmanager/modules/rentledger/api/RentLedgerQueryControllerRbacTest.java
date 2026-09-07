package com.rentmanager.modules.rentledger.api;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.rentledger.api.dto.response.RentLedgerEntryResponse;
import com.rentmanager.modules.rentledger.application.query.service.RentLedgerQueryService;
import com.rentmanager.modules.rentledger.domain.enums.RentLedgerStatus;
import com.rentmanager.modules.support.MockTenantAuthentication;
import com.rentmanager.modules.support.TestSecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC test slice for RentLedgerQueryController. First method-security test
 * suite of its kind in this codebase -- follows PropertyApiTest's
 * @SpringBootTest(webEnvironment = RANDOM_PORT) + MockMvc + TestSecurityConfig
 * convention rather than @WebMvcTest, since that's the established pattern
 * for hitting real controllers through the real security filter chain in
 * this repo (see PropertyApiTest). Confirmed empirically (see the now-removed
 * diagnostic test) that @PreAuthorize IS enforced under the "test" profile
 * despite SecurityConfig itself being @Profile("!test") -- so this test is
 * exercising real method security, not a no-op.
 *
 * Only the service layer is mocked (@MockBean RentLedgerQueryService), not
 * the repositories underneath it, since the point here is purely to prove
 * the @PreAuthorize gate on the controller, not re-test service logic
 * (already covered by RentLedgerQueryServiceImplTest).
 *
 * Coverage: STAFF is the lowest role permitted per the controller's own
 * documented RBAC tier (OWNER+MANAGER+STAFF on all four endpoints) -- so
 * proving STAFF gets 200 on every endpoint is the strongest single check
 * that nobody forgot to copy the @PreAuthorize annotation onto one of the
 * four methods. A denied/unrecognized role is checked against two of the
 * four endpoints (getById and the newest one, getTransactionsForEntry) as
 * a cheap insurance check that denial isn't accidentally endpoint-specific.
 *
 * NOT covered here: unauthenticated (401) behavior. TestSecurityConfig's
 * filter chain is anyRequest().permitAll() with no oauth2ResourceServer /
 * exceptionHandling wiring, so a request with no securityContext(...) at
 * all would hit the controller with an empty/anonymous Authentication and
 * be rejected by @PreAuthorize as 403, not 401 -- that 401-vs-403 distinction
 * is a property of SecurityConfig's real filter chain (CustomAuthentication-
 * EntryPoint), which isn't active under this profile. Testing 401 here would
 * measure the wrong thing; if 401 behavior needs coverage, it belongs in a
 * test that loads the real (non-"test") SecurityConfig instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
class RentLedgerQueryControllerRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RentLedgerQueryService rentLedgerQueryService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ENTRY_ID = UUID.randomUUID();
    private static final UUID LEASE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(rentLedgerQueryService.getById(any(), any())).thenReturn(dummyEntry());
        when(rentLedgerQueryService.getByLease(any(), any())).thenReturn(List.of());
        when(rentLedgerQueryService.getByStatus(any(), any())).thenReturn(List.of());
        when(rentLedgerQueryService.getTransactionsForEntry(any(), any())).thenReturn(List.of());
    }

    @Nested
    class AllowedRoles {

        @Test
        void staffCanGetById() throws Exception {
            mockMvc.perform(get("/api/v1/rent-ledger/entries/" + ENTRY_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isOk());
        }

        @Test
        void staffCanGetByLease() throws Exception {
            mockMvc.perform(get("/api/v1/rent-ledger/leases/" + LEASE_ID + "/entries")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isOk());
        }

        @Test
        void staffCanGetByStatus() throws Exception {
            mockMvc.perform(get("/api/v1/rent-ledger/entries/status/" + RentLedgerStatus.DUE)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isOk());
        }

        @Test
        void staffCanGetTransactionsForEntry() throws Exception {
            mockMvc.perform(get("/api/v1/rent-ledger/entries/" + ENTRY_ID + "/transactions")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class DeniedRoles {

        @Test
        void unrecognizedRoleIsRejectedOnGetById() throws Exception {
            mockMvc.perform(get("/api/v1/rent-ledger/entries/" + ENTRY_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_NOT_A_REAL_ROLE")))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(rentLedgerQueryService);
        }

        @Test
        void unrecognizedRoleIsRejectedOnGetTransactionsForEntry() throws Exception {
            mockMvc.perform(get("/api/v1/rent-ledger/entries/" + ENTRY_ID + "/transactions")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_NOT_A_REAL_ROLE")))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(rentLedgerQueryService);
        }
    }

    private RentLedgerEntryResponse dummyEntry() {
        return new RentLedgerEntryResponse(
                ENTRY_ID,
                LEASE_ID,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                LocalDate.now(),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                RentLedgerStatus.DUE.name(),
                false,
                0L,
                null, null, null, null
        );
    }
}