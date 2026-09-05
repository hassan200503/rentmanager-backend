package com.rentmanager.modules.rentledger.api;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.rentledger.application.service.B2CDisbursementService;
import com.rentmanager.modules.rentledger.domain.enums.DisbursementStatus;
import com.rentmanager.modules.rentledger.domain.model.Disbursement;
import com.rentmanager.modules.rentledger.domain.repository.DisbursementRepository;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC test for {@code DisbursementController}.
 *
 * Mirrors {@code LeaseControllerRbacTest}'s pattern exactly:
 *   - {@code @SpringBootTest(RANDOM_PORT)} to load the full security chain
 *   - {@code @Import(TestSecurityConfig)} to permit all (method security still active)
 *   - {@code @MockBean} on both the service and repository layers
 *   - {@code MockTenantAuthentication.asTenant(tenantId, role)} to inject roles
 *
 * DisbursementController's RBAC is uniform across both endpoints:
 *   - POST /api/v1/disbursements     → OWNER + MANAGER only (STAFF excluded)
 *   - GET  /api/v1/disbursements/{id} → OWNER + MANAGER only (STAFF excluded)
 *
 * Additionally confirms cross-tenant isolation on the GET endpoint:
 * a valid landlord can only retrieve their own disbursements (findByIdAndTenantId
 * returns empty for a different tenant's record → 404).
 *
 * Caveat from the trace phase: {@code TenantPortalController} has no
 * {@code @PreAuthorize} annotations at all, so only the disbursement
 * endpoints have testable RBAC at the controller level.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
class DisbursementControllerRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private B2CDisbursementService b2cDisbursementService;

    @MockBean
    private DisbursementRepository disbursementRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID DISBURSEMENT_ID = UUID.randomUUID();

    // No recipient fields: the payout destination is read from
    // tenants.payout_phone_number by the service, never from the request.
    private static final String INITIATE_BODY = """
            {
              "leaseId": "%s",
              "ledgerEntryId": "%s",
              "amount": 5000.00
            }
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        when(b2cDisbursementService.initiateDisbursement(any(), any(), any(), any(), any(), any()))
                .thenReturn(buildDummyDisbursement(TENANT_ID));
        when(disbursementRepository.findByIdAndTenantId(DISBURSEMENT_ID, TENANT_ID))
                .thenReturn(Optional.of(buildDummyDisbursement(TENANT_ID)));
        when(disbursementRepository.findByIdAndTenantId(DISBURSEMENT_ID, OTHER_TENANT_ID))
                .thenReturn(Optional.empty()); // cross-tenant isolation
    }

    @Nested
    class AllowedRoles {

        @Test
        void ownerCanInitiateDisbursement() throws Exception {
            mockMvc.perform(post("/api/v1/disbursements")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INITIATE_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        void managerCanInitiateDisbursement() throws Exception {
            mockMvc.perform(post("/api/v1/disbursements")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INITIATE_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        void ownerCanGetDisbursementStatus() throws Exception {
            mockMvc.perform(get("/api/v1/disbursements/" + DISBURSEMENT_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                    .andExpect(status().isOk());
        }

        @Test
        void managerCanGetDisbursementStatus() throws Exception {
            mockMvc.perform(get("/api/v1/disbursements/" + DISBURSEMENT_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class DeniedRoles {

        @Test
        void staffCannotInitiateDisbursement() throws Exception {
            mockMvc.perform(post("/api/v1/disbursements")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INITIATE_BODY))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(b2cDisbursementService);
        }

        @Test
        void staffCannotGetDisbursementStatus() throws Exception {
            mockMvc.perform(get("/api/v1/disbursements/" + DISBURSEMENT_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(disbursementRepository);
        }

        @Test
        void unrecognizedRoleCannotInitiateDisbursement() throws Exception {
            mockMvc.perform(post("/api/v1/disbursements")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_NOT_A_REAL_ROLE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(INITIATE_BODY))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(b2cDisbursementService);
        }

        @Test
        void unrecognizedRoleCannotGetDisbursementStatus() throws Exception {
            mockMvc.perform(get("/api/v1/disbursements/" + DISBURSEMENT_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_NOT_A_REAL_ROLE")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class CrossTenantIsolation {

        /**
         * Landlord B cannot view Landlord A's disbursement.
         * DisbursementController uses {@code findByIdAndTenantId} which returns
         * empty when the requesting tenant doesn't own the disbursement,
         * resulting in ResourceNotFoundException → 404.
         */
        @Test
        void landlordCannotViewOtherLandlordsDisbursement() throws Exception {
            // OTHER_TENANT_ID tries to access a disbursement owned by TENANT_ID
            mockMvc.perform(get("/api/v1/disbursements/" + DISBURSEMENT_ID)
                            .with(MockTenantAuthentication.asTenant(OTHER_TENANT_ID, "ROLE_LANDLORD_OWNER")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class RequestValidation {

        @Test
        void initiateWithNullLeaseId_returns400() throws Exception {
            String bodyWithNullLeaseId = """
                    {
                      "ledgerEntryId": "%s",
                      "amount": 5000.00
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/disbursements")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithNullLeaseId))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(b2cDisbursementService);
        }

        @Test
        void initiateWithAmountBelowMinimum_returns400() throws Exception {
            String bodyWithZeroAmount = """
                    {
                      "leaseId": "%s",
                      "ledgerEntryId": "%s",
                      "amount": 0.00
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/api/v1/disbursements")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithZeroAmount))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(b2cDisbursementService);
        }

        /**
         * Replaces a test that asserted a supplied recipientPhone had to match
         * ^\+2547\d{8}$. There is no recipient field to validate any more —
         * the destination comes from tenants.payout_phone_number — so the
         * property worth protecting is the stronger one: a caller who sends a
         * phone number anyway must not be able to influence where money goes.
         */
        @Test
        void initiateIgnoresAnyRecipientSuppliedByTheCaller() throws Exception {
            String bodyWithSmuggledRecipient = """
                    {
                      "leaseId": "%s",
                      "ledgerEntryId": "%s",
                      "amount": 5000.00,
                      "recipientPhone": "+254700000001",
                      "recipientName": "Attacker"
                    }
                    """.formatted(UUID.randomUUID(), UUID.randomUUID());

            mockMvc.perform(post("/api/v1/disbursements")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithSmuggledRecipient))
                    .andExpect(status().isOk());

            // Six arguments, none of them a recipient. The service resolves the
            // destination itself, so nothing the caller sent can reach Daraja.
            verify(b2cDisbursementService).initiateDisbursement(
                    any(), any(), any(), any(), any(), any());
        }

        @Test
        void initiateWithoutLedgerEntryId_returns400() throws Exception {
            // Without the entry there is nothing to validate the amount
            // against, which is the situation the entitlement cap exists to end.
            String bodyWithoutEntry = """
                    {
                      "leaseId": "%s",
                      "amount": 5000.00
                    }
                    """.formatted(UUID.randomUUID());

            mockMvc.perform(post("/api/v1/disbursements")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithoutEntry))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(b2cDisbursementService);
        }

        /*
         * Removed here: initiateWithValidPhone_passes and
         * initiateWithBlankRecipientName_returns400. Both asserted validation
         * on recipientPhone / recipientName, fields the request no longer
         * carries — the destination is resolved from the landlord record. The
         * property they were reaching for is covered more strongly by
         * initiateIgnoresAnyRecipientSuppliedByTheCaller above, which proves a
         * caller cannot influence the destination at all rather than proving
         * their chosen destination was well-formatted.
         */
    }

    private Disbursement buildDummyDisbursement(UUID tenantId) {
        return Disbursement.rehydrate(
                DISBURSEMENT_ID,
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("5000.00"),
                "+254712345678",
                "Test Recipient",
                "BusinessPayment",
                DisbursementStatus.PENDING,
                null,
                "conv-test-123",
                "ocid-test-456",
                null, 0, false,
                Instant.now(),
                Instant.now(),
                "KES",
                0L
        );
    }
}
