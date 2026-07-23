package com.rentmanager.modules.lease.api;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.contract.common.PageResponse;
import com.rentmanager.modules.lease.application.dto.response.LeaseActionResponse;
import com.rentmanager.modules.lease.application.dto.response.LeaseDetailResponse;
import com.rentmanager.modules.lease.application.dto.response.LeaseResponse;
import com.rentmanager.modules.lease.application.dto.response.LeaseSummaryResponse;
import com.rentmanager.modules.lease.application.dto.request.BillingCycleDTO;
import com.rentmanager.modules.lease.application.dto.request.LeaseStatusDTO;
import com.rentmanager.modules.lease.application.dto.request.LeaseTypeDTO;
import com.rentmanager.modules.lease.application.service.LeaseApplicationService;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC test slice for LeaseController. Mirrors RentLedgerQueryControllerRbacTest's
 * conventions (SpringBootTest RANDOM_PORT + MockMvc + TestSecurityConfig, only
 * the application service mocked, real @PreAuthorize chain exercised) -- see
 * that class's javadoc for why this pattern is used over @WebMvcTest and why
 * method security is empirically confirmed active under the "test" profile
 * despite SecurityConfig itself being @Profile("!test").
 *
 * UNLIKE RentLedgerQueryController, LeaseController's RBAC is NOT uniform
 * across its endpoints (see LeaseController's own class javadoc):
 *   - create, update, action: OWNER+MANAGER (STAFF excluded)
 *   - getById, search:        OWNER+MANAGER+STAFF
 *   - delete:                 OWNER only (MANAGER excluded)
 *
 * So "prove the lowest permitted role gets 200 on every endpoint" isn't a
 * single check here -- AllowedRoles proves the lowest permitted role per
 * endpoint, and DeniedRoles additionally proves each endpoint's specifically
 * *excluded* role (STAFF on the three write endpoints, MANAGER on delete) is
 * rejected, on top of the unrecognized-role checks RentLedger's suite also
 * has. This is the piece RentLedger's uniform RBAC didn't need to cover.
 *
 * create/update/action take @Valid @RequestBody -- request bodies below are
 * built to satisfy validation (real DTO constraints, not guessed) so a 403
 * assertion is actually proving authorization was denied, not incidentally
 * passing because a 400 happened to arrive first from a malformed body.
 * ACTIVATE was chosen for the action-endpoint body because it's the one
 * LeaseActionType that requires neither `reason` nor `terminationType`
 * (@AssertTrue on LeaseActionRequest only requires those for REJECT/TERMINATE).
 *
 * Only the service layer is mocked (@MockBean LeaseApplicationService), same
 * reasoning as RentLedger's suite -- proving the @PreAuthorize gate, not
 * re-testing service logic.
 *
 * NOT covered here: unauthenticated (401) behavior -- same reasoning as
 * RentLedgerQueryControllerRbacTest; TestSecurityConfig has no
 * oauth2ResourceServer/exceptionHandling wiring, so that distinction belongs
 * to a test loading the real SecurityConfig instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
class LeaseControllerRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LeaseApplicationService leaseService;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID LEASE_ID = UUID.randomUUID();
    private static final UUID PROPERTY_ID = UUID.randomUUID();
    private static final UUID UNIT_ID = UUID.randomUUID();
    private static final UUID TENANT_PROFILE_ID = UUID.randomUUID();
    private static final UUID PERFORMED_BY = UUID.randomUUID();

    private static final String CREATE_BODY = """
            {
              "propertyId": "%s",
              "unitId": "%s",
              "tenantProfileId": "%s",
              "leaseNumber": "LSE-RBAC-TEST",
              "leaseType": "FIXED_TERM",
              "billingCycle": "MONTHLY",
              "startDate": "2026-01-01",
              "endDate": "2027-01-01",
              "rentAmount": 10000,
              "securityDeposit": 10000,
              "lateFeeAmount": 0,
              "gracePeriodDays": 5,
              "autoRenew": false
            }
            """.formatted(PROPERTY_ID, UNIT_ID, TENANT_PROFILE_ID);

    private static final String UPDATE_BODY = """
            {
              "startDate": "2026-01-01",
              "endDate": "2027-01-01",
              "rentAmount": 10000,
              "securityDeposit": 10000,
              "lateFeeAmount": 0,
              "gracePeriodDays": 5,
              "autoRenew": false
            }
            """;

    private static final String ACTION_BODY = """
            {
              "performedBy": "%s",
              "action": "ACTIVATE",
              "actionDate": "2026-01-01"
            }
            """.formatted(PERFORMED_BY);

    @BeforeEach
    void setUp() {
        when(leaseService.create(any())).thenReturn(dummyLeaseResponse());
        when(leaseService.update(any(), any())).thenReturn(dummyLeaseResponse());
        when(leaseService.getById(any())).thenReturn(dummyDetailResponse());
        when(leaseService.executeAction(any(), any())).thenReturn(dummyActionResponse());
        when(leaseService.search(any())).thenReturn(dummyPage());
    }

    @Nested
    class AllowedRoles {

        @Test
        void managerCanCreate() throws Exception {
            mockMvc.perform(post("/api/v1/leases")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        void managerCanUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/leases/" + LEASE_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        void staffCanGetById() throws Exception {
            mockMvc.perform(get("/api/v1/leases/" + LEASE_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isOk());
        }

        @Test
        void managerCanExecuteAction() throws Exception {
            mockMvc.perform(post("/api/v1/leases/" + LEASE_ID + "/action")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ACTION_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        void ownerCanDelete() throws Exception {
            mockMvc.perform(delete("/api/v1/leases/" + LEASE_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                    .andExpect(status().isOk());
        }

        @Test
        void staffCanSearch() throws Exception {
            mockMvc.perform(get("/api/v1/leases")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class DeniedRoles {

        @Test
        void staffCannotCreate() throws Exception {
            mockMvc.perform(post("/api/v1/leases")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CREATE_BODY))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(leaseService);
        }

        @Test
        void staffCannotUpdate() throws Exception {
            mockMvc.perform(put("/api/v1/leases/" + LEASE_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(UPDATE_BODY))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(leaseService);
        }

        @Test
        void staffCannotExecuteAction() throws Exception {
            mockMvc.perform(post("/api/v1/leases/" + LEASE_ID + "/action")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ACTION_BODY))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(leaseService);
        }

        @Test
        void managerCannotDelete() throws Exception {
            mockMvc.perform(delete("/api/v1/leases/" + LEASE_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER")))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(leaseService);
        }

        @Test
        void unrecognizedRoleIsRejectedOnGetById() throws Exception {
            mockMvc.perform(get("/api/v1/leases/" + LEASE_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_NOT_A_REAL_ROLE")))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(leaseService);
        }

        @Test
        void unrecognizedRoleIsRejectedOnSearch() throws Exception {
            mockMvc.perform(get("/api/v1/leases")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_NOT_A_REAL_ROLE")))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(leaseService);
        }
    }

    private LeaseResponse dummyLeaseResponse() {
        return new LeaseResponse(
                LEASE_ID,
                "LSE-RBAC-TEST",
                PROPERTY_ID,
                UNIT_ID,
                TENANT_PROFILE_ID,
                LeaseType.FIXED_TERM,
                BillingCycleDTO.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                5,
                false,
                "ACTIVE",
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }



    private LeaseDetailResponse dummyDetailResponse() {
        // UPDATED: LeaseDetailResponse extended this session with 8 new
        // lifecycle-metadata fields (signedAt through terminationReason).
        // All null here -- this fixture represents an ACTIVE lease that
        // hasn't been signed/terminated/expired/renewed/cancelled in the
        // mock's own timeline, so null is the correct value, not a filler.
        return new LeaseDetailResponse(
                LEASE_ID,
                "LSE-RBAC-TEST",
                TENANT_ID,
                PROPERTY_ID,
                UNIT_ID,
                LeaseTypeDTO.FIXED_TERM,
                BillingCycleDTO.MONTHLY,
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                BigDecimal.TEN,
                BigDecimal.TEN,
                LeaseStatusDTO.ACTIVE,
                5,
                false,
                LocalDate.now(),
                LocalDate.now(),
                0L,
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








    private LeaseActionResponse dummyActionResponse() {
        // Constructor shape confirmed from the real call site in
        // LeaseApplicationService.executeAction(): new LeaseActionResponse(
        // saved.getId(), previous, map(saved.getStatus()), request.getAction().name()),
        // where previous and map(saved.getStatus()) are both LeaseStatusDTO.
        return new LeaseActionResponse(
                LEASE_ID,
                LeaseStatusDTO.PENDING_ACTIVATION,
                LeaseStatusDTO.ACTIVE,
                "ACTIVATE"
        );
    }

    private PageResponse<LeaseSummaryResponse> dummyPage() {
        LeaseSummaryResponse summary = new LeaseSummaryResponse(
                LEASE_ID,
                "LSE-RBAC-TEST",
                LeaseStatusDTO.ACTIVE,
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                BigDecimal.TEN,
                "Test Tenant",
                "+254700000000"
        );
        // Shape confirmed from LeaseApplicationService.search(): new PageResponse<>(
        // result, request.page(), request.size(), result.size(), 1, true, true)
        return new PageResponse<>(List.of(summary), 0, 10, 1, 1, true, true);
    }
}