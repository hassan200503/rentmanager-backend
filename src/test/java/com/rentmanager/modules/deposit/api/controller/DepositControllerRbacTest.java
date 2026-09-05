package com.rentmanager.modules.deposit.api.controller;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.deposit.application.service.DepositCommandService;
import com.rentmanager.modules.deposit.domain.model.Deposit;
import com.rentmanager.modules.deposit.domain.repository.DepositRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC test for {@code DepositController}. Mirrors
 * {@code DisbursementControllerRbacTest}'s pattern exactly.
 *
 * RBAC:
 *   - GET  /api/v1/deposits/{id}          -> OWNER + MANAGER + STAFF
 *   - GET  /api/v1/deposits/lease/{id}    -> OWNER + MANAGER + STAFF
 *   - POST /api/v1/deposits/{id}/refund   -> OWNER + MANAGER only (STAFF excluded)
 *   - POST /api/v1/deposits/{id}/forfeit  -> OWNER + MANAGER only (STAFF excluded)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
class DepositControllerRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DepositCommandService depositCommandService;

    @MockBean
    private DepositRepository depositRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();
    private static final UUID DEPOSIT_ID = UUID.randomUUID();
    private static final UUID LEASE_ID = UUID.randomUUID();

    private static final String REFUND_BODY = """
            { "refundAmount": 1000.00 }
            """;

    @BeforeEach
    void setUp() {
        lenient().when(depositRepository.findByIdAndTenantId(DEPOSIT_ID, TENANT_ID))
                .thenReturn(Optional.of(buildDummyDeposit(TENANT_ID)));
        lenient().when(depositRepository.findByIdAndTenantId(DEPOSIT_ID, OTHER_TENANT_ID))
                .thenReturn(Optional.empty()); // cross-tenant isolation
        lenient().when(depositRepository.findByLeaseIdAndTenantId(LEASE_ID, TENANT_ID))
                .thenReturn(Optional.of(buildDummyDeposit(TENANT_ID)));
        lenient().when(depositCommandService.refundDeposit(eq(TENANT_ID), eq(DEPOSIT_ID), any()))
                .thenReturn(buildDummyDeposit(TENANT_ID));
        lenient().when(depositCommandService.forfeitDeposit(TENANT_ID, DEPOSIT_ID))
                .thenReturn(buildDummyDeposit(TENANT_ID));
    }

    @Nested
    class AllowedRoles {

        @Test
        void ownerCanGetDepositById() throws Exception {
            mockMvc.perform(get("/api/v1/deposits/" + DEPOSIT_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER")))
                    .andExpect(status().isOk());
        }

        @Test
        void staffCanGetDepositById() throws Exception {
            mockMvc.perform(get("/api/v1/deposits/" + DEPOSIT_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isOk());
        }

        @Test
        void managerCanGetDepositByLease() throws Exception {
            mockMvc.perform(get("/api/v1/deposits/lease/" + LEASE_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER")))
                    .andExpect(status().isOk());
        }

        @Test
        void ownerCanRefundDeposit() throws Exception {
            mockMvc.perform(post("/api/v1/deposits/" + DEPOSIT_ID + "/refund")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REFUND_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        void managerCanForfeitDeposit() throws Exception {
            mockMvc.perform(post("/api/v1/deposits/" + DEPOSIT_ID + "/forfeit")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_MANAGER")))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class DeniedRoles {

        @Test
        void staffCannotRefundDeposit() throws Exception {
            mockMvc.perform(post("/api/v1/deposits/" + DEPOSIT_ID + "/refund")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REFUND_BODY))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(depositCommandService);
        }

        @Test
        void staffCannotForfeitDeposit() throws Exception {
            mockMvc.perform(post("/api/v1/deposits/" + DEPOSIT_ID + "/forfeit")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_STAFF")))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(depositCommandService);
        }

        @Test
        void unrecognizedRoleCannotGetDepositById() throws Exception {
            mockMvc.perform(get("/api/v1/deposits/" + DEPOSIT_ID)
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_NOT_A_REAL_ROLE")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void unrecognizedRoleCannotRefundDeposit() throws Exception {
            mockMvc.perform(post("/api/v1/deposits/" + DEPOSIT_ID + "/refund")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_NOT_A_REAL_ROLE"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REFUND_BODY))
                    .andExpect(status().isForbidden());

            verifyNoInteractions(depositCommandService);
        }
    }

    @Nested
    class CrossTenantIsolation {

        @Test
        void landlordCannotViewOtherLandlordsDeposit() throws Exception {
            mockMvc.perform(get("/api/v1/deposits/" + DEPOSIT_ID)
                            .with(MockTenantAuthentication.asTenant(OTHER_TENANT_ID, "ROLE_LANDLORD_OWNER")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class RequestValidation {

        @Test
        void refundWithNullAmount_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/deposits/" + DEPOSIT_ID + "/refund")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(depositCommandService);
        }

        @Test
        void refundWithZeroAmount_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/deposits/" + DEPOSIT_ID + "/refund")
                            .with(MockTenantAuthentication.asTenant(TENANT_ID, "ROLE_LANDLORD_OWNER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"refundAmount\": 0.00 }"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(depositCommandService);
        }
    }

    private Deposit buildDummyDeposit(UUID tenantId) {
        Deposit deposit = Deposit.create(
                tenantId, LEASE_ID, UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("1000.00"), "corr-test", "KES"
        );
        deposit.confirmPayment(new BigDecimal("1000.00"), "corr-test");
        deposit.pullDomainEvents();
        return deposit;
    }
}
