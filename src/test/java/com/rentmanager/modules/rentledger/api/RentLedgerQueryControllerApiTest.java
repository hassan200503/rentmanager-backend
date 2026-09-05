package com.rentmanager.modules.rentledger.api;

import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.lease.domain.enums.BillingCycle;
import com.rentmanager.modules.lease.domain.enums.LeaseType;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.repository.LeaseRepository;
import com.rentmanager.modules.rentledger.application.service.RentLedgerApplicationService;
import com.rentmanager.modules.rentledger.domain.model.RentLedgerEntry;
import com.rentmanager.modules.support.MinimalTenantChainFixture;
import com.rentmanager.modules.support.MockTenantAuthentication;
import com.rentmanager.modules.support.TestSecurityConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer verification of RentLedgerQueryController, per Addendum 2
 * §4.2. Mirrors PropertyApiTest's annotation stack exactly (RANDOM_PORT +
 * AutoConfigureMockMvc + Transactional + TestSecurityConfig import) rather
 * than extending AbstractPostgresIntegrationTest — that base has no MockMvc
 * or security wiring, so it cannot support @AuthenticationPrincipal
 * resolution, which every test here depends on via MockTenantAuthentication.
 *
 * Unlike PropertyApiTest, RentLedgerEntryRepository is NOT mocked. Per
 * explicit instruction, all fixture data is seeded through the real
 * RentLedgerApplicationService.postCharge(...) against a real persisted
 * Lease, exercising the actual RentLedgerEntryResponse mapping and real
 * Postgres constraints rather than a hand-built mock shape.
 *
 * Cross-tenant isolation is asserted per-endpoint according to its actual
 * scoping mechanism, not forced into a single shared expectation:
 *  - getById: RentLedgerEntryRepository.findByIdAndTenantId(...) returns
 *    empty for the wrong tenant -> RentLedgerEntryNotFoundException -> 404,
 *    same pattern as PropertyNotFoundException / PropertyApiTest's
 *    shouldEnforceTenantIsolation.
 *  - getByLease: findByLease(tenantId, leaseId) is list-scoped by tenant at
 *    the query level, not an id-lookup-or-throw. A cross-tenant request
 *    doesn't hit a "not found" path at all -- it correctly returns 200 with
 *    an empty list, because from that tenant's perspective no such lease
 *    exists in their scope. Asserting 404 here would be wrong, not stricter.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
class RentLedgerQueryControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LeaseRepository leaseRepository;

    @Autowired
    private RentLedgerApplicationService rentLedgerApplicationService;

    @Autowired
    private EntityManager entityManager;

    private static final UUID TENANT_A =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final UUID TENANT_B =
            UUID.fromString("44444444-4444-4444-4444-444444444444");

    private Lease tenantALease;
    private RentLedgerEntry tenantAEntry;

    /**
     * Full-month lease starting on the 1st, matching
     * RentLedgerApplicationServiceIntegrationTest's non-prorated setup --
     * proration itself is already covered there and is out of scope for
     * this controller-layer suite, which is only verifying routing,
     * tenant scoping, response mapping, and the 404 contract.
     */
    @BeforeEach
    void setUp() {
        entityManager.createNativeQuery("INSERT INTO tenants (id, tenant_code, name) VALUES (?1, ?2, 'Test Landlord')")
                .setParameter(1, TENANT_A).setParameter(2, "TEN-" + TENANT_A).executeUpdate();

        UUID propertyId = MinimalTenantChainFixture.persistProperty(entityManager, TENANT_A);
        UUID unitId = MinimalTenantChainFixture.persistUnit(entityManager, TENANT_A, propertyId);
        UUID tenantProfileId = MinimalTenantChainFixture.persistTenantProfile(entityManager, TENANT_A);

        Lease lease = Lease.create(
                TENANT_A,
                propertyId,
                unitId,
                tenantProfileId,
                "LSE-API-" + UUID.randomUUID(),
                LeaseType.FIXED_TERM,
                BillingCycle.MONTHLY,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2027, 6, 1),
                new BigDecimal("1000.00"),
                new BigDecimal("1000.00"),
                BigDecimal.ZERO,
                5,
                false
        );
        tenantALease = leaseRepository.save(lease);
        entityManager.flush();

        tenantAEntry = rentLedgerApplicationService.postCharge(
                TENANT_A, "corr-api-1", tenantALease.getId(),
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 6, 1)
        );
        entityManager.flush();
    }

    @Test
    void shouldGetEntryByIdSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/rent-ledger/entries/" + tenantAEntry.getId())
                        .with(MockTenantAuthentication.asTenant(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(tenantAEntry.getId().toString()))
                .andExpect(jsonPath("$.data.leaseId").value(tenantALease.getId().toString()))
                .andExpect(jsonPath("$.data.status").value("DUE"))
                .andExpect(jsonPath("$.data.amountDue").value(1000.00))
                .andExpect(jsonPath("$.data.prorated").value(false));
    }

    @Test
    void shouldGetEntriesByLeaseSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/rent-ledger/leases/" + tenantALease.getId() + "/entries")
                        .with(MockTenantAuthentication.asTenant(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(tenantAEntry.getId().toString()))
                .andExpect(jsonPath("$.data[0].leaseId").value(tenantALease.getId().toString()));
    }

    @Test
    void shouldGetEntriesByStatusSuccessfully() throws Exception {
        mockMvc.perform(get("/api/v1/rent-ledger/entries/status/DUE")
                        .with(MockTenantAuthentication.asTenant(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(tenantAEntry.getId().toString()))
                .andExpect(jsonPath("$.data[0].status").value("DUE"));
    }

    @Test
    void shouldReturn404ForMissingEntry() throws Exception {
        UUID missingEntryId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/rent-ledger/entries/" + missingEntryId)
                        .with(MockTenantAuthentication.asTenant(TENANT_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RENT_LEDGER_ENTRY_NOT_FOUND"));
    }

    @Test
    void shouldEnforceTenantIsolationOnGetById() throws Exception {
        // Cross-tenant single-entry lookup: findByIdAndTenantId(...) finds
        // nothing under TENANT_B's scope, so this hits the exact same
        // RentLedgerEntryNotFoundException -> 404 path as a genuinely
        // missing id -- the record is invisible to this tenant, same
        // reasoning as PropertyApiTest.shouldEnforceTenantIsolation.
        mockMvc.perform(get("/api/v1/rent-ledger/entries/" + tenantAEntry.getId())
                        .with(MockTenantAuthentication.asTenant(TENANT_B)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RENT_LEDGER_ENTRY_NOT_FOUND"));
    }

    @Test
    void shouldEnforceTenantIsolationOnGetByLease() throws Exception {
        // Cross-tenant lease-scoped lookup: correctly returns 200 with an
        // empty list, NOT 404. findByLease(tenantId, leaseId) is inherently
        // list-scoped by tenant, so from TENANT_B's perspective the lease
        // simply has no entries in their scope -- there is no "not found"
        // exception path for this endpoint to hit.
        mockMvc.perform(get("/api/v1/rent-ledger/leases/" + tenantALease.getId() + "/entries")
                        .with(MockTenantAuthentication.asTenant(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}