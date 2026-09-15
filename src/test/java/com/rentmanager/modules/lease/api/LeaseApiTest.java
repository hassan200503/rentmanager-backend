package com.rentmanager.modules.lease.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.rentmanager.crossmodule.support.PostgresSpringBridge;
import com.rentmanager.modules.support.MockTenantAuthentication;
import com.rentmanager.modules.support.TestSecurityConfig;
import com.rentmanager.modules.tenant.domain.enums.TenantType;
import com.rentmanager.modules.tenant.domain.model.Tenant;
import com.rentmanager.modules.tenant.domain.repository.TenantRepository;
import com.rentmanager.modules.tenant.renter.domain.model.TenantProfile;
import com.rentmanager.modules.tenant.renter.domain.repository.TenantProfileRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Transactional
@Rollback
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ContextConfiguration(initializers = PostgresSpringBridge.class)
@Import(TestSecurityConfig.class)
public class LeaseApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TenantProfileRepository tenantProfileRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private EntityManager entityManager;

    private static final UUID TENANT_A =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID TENANT_B =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    // Must match the propertyId/unitId hardcoded in the createLease() payload below.
    private static final UUID PROPERTY_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final UUID UNIT_ID =
            UUID.fromString("44444444-4444-4444-4444-444444444444");

    // Must match the tenantProfileId hardcoded in the createLease() payload below.
    private static final UUID TENANT_PROFILE_ID =
            UUID.fromString("55555555-5555-5555-5555-555555555555");

    @BeforeEach
    void seedFixtures() {
        // tenant_profile.tenant_id has an FK to tenants(id), so both landlord
        // accounts referenced in this test (TENANT_A does the creating,
        // TENANT_B does the cross-tenant read) must exist first. create()
        // never assigns an id itself (BaseEntity only auto-generates one via
        // @PrePersist if it's still null) so restoreId(...) is used to pin
        // each Tenant to the fixed UUID these tests already reference
        // throughout, before either row is persisted.
        seedTenant(TENANT_A);
        seedTenant(TENANT_B);

        // leases.property_id/unit_id also carry real foreign keys now
        // (V73-V74) — pin a property/unit to the fixed UUIDs the payload
        // below already references, same rationale as TENANT_PROFILE_ID.
        entityManager.createNativeQuery("""
                INSERT INTO properties (id, tenant_id, reference_code, name, status, created_at, premises_type)
                VALUES (?1, ?2, ?3, 'Test Property', 'ACTIVE', NOW(), 'RESIDENTIAL')
                """)
                .setParameter(1, PROPERTY_ID).setParameter(2, TENANT_A).setParameter(3, "PROP-" + PROPERTY_ID)
                .executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO units (id, unit_number, tenant_id, property_id, status, occupancy_status)
                VALUES (?1, ?2, ?3, ?4, 'ACTIVE', 'VACANT')
                """)
                .setParameter(1, UNIT_ID).setParameter(2, "U-" + UNIT_ID)
                .setParameter(3, TENANT_A).setParameter(4, PROPERTY_ID)
                .executeUpdate();

        // LeaseApplicationService.create() looks this up and checks it belongs
        // to the calling landlord (TENANT_A) before allowing lease creation.
        // rehydrate() (rather than create()) is used because it lets us pin
        // the id to the fixed UUID the test payload already references;
        // create() always assigns a random id.
        TenantProfile profile = TenantProfile.rehydrate(
                TENANT_PROFILE_ID,
                TENANT_A,
                "clerk_test_user_" + TENANT_PROFILE_ID,
                "Test Renter",
                "renter@test.com",
                "0700000000",
                null
        );
        tenantProfileRepository.save(profile);
    }

    private void seedTenant(UUID id) {
        Tenant tenant = Tenant.create(
                "TC-" + id,
                "Test Landlord " + id,
                "test-landlord-" + id,
                "landlord-" + id + "@test.com",
                "0700000001",
                TenantType.STANDARD
        );
        tenant.restoreId(id);
        tenantRepository.save(tenant);
    }

    private String createLease(UUID tenantId) throws Exception {

        String payload = """
        {
          "propertyId": "33333333-3333-3333-3333-333333333333",
          "unitId": "44444444-4444-4444-4444-444444444444",
          "tenantProfileId": "55555555-5555-5555-5555-555555555555",
          "leaseNumber": "LS-%s",
          "leaseType": "FIXED_TERM",
          "billingCycle": "MONTHLY",
          "startDate": "2026-06-02",
          "endDate": "2026-12-01",
          "rentAmount": 20000,
          "securityDeposit": 30000,
          "lateFeeAmount": 1000,
          "gracePeriodDays": 7,
          "autoRenew": false
        }
        """.formatted(System.currentTimeMillis());

        MvcResult result = mockMvc.perform(post("/api/v1/leases")
                        .with(MockTenantAuthentication.asTenant(tenantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.data.id"
        );
    }

    private void performAction(String leaseId, String action) throws Exception {

        String payload = """
        {
          "performedBy": "%s",
          "action": "%s",
          "reason": "integration-test"
        }
        """.formatted(UUID.randomUUID(), action);

        mockMvc.perform(post("/api/v1/leases/%s/action".formatted(leaseId))
                        .with(MockTenantAuthentication.asTenant(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCreateLeaseSuccessfully() throws Exception {

        String leaseId = createLease(TENANT_A);

        assertFalse(leaseId.isBlank());
    }

    @Test
    void shouldBlockCrossTenantAccess() throws Exception {

        String leaseId = createLease(TENANT_A);

        mockMvc.perform(get("/api/v1/leases/" + leaseId)
                        .with(MockTenantAuthentication.asTenant(TENANT_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void seedHeldDeposit(String leaseId) {
        entityManager.createNativeQuery("""
                INSERT INTO deposits (id, tenant_id, lease_id, unit_id, tenant_profile_id,
                                      amount_required, amount_paid, status, paid_at)
                VALUES (?1, ?2, ?3, ?4, ?5, 30000, 30000, 'HELD', NOW())
                """)
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, TENANT_A)
                .setParameter(3, UUID.fromString(leaseId))
                .setParameter(4, UNIT_ID)
                .setParameter(5, TENANT_PROFILE_ID)
                .executeUpdate();
    }

    @Test
    void shouldRunFullLeaseLifecycle() throws Exception {

        String leaseId = createLease(TENANT_A);

        performAction(leaseId, "APPROVE");
        performAction(leaseId, "AWAITING_DEPOSIT");
        // A lease with securityDeposit > 0 requires a HELD deposit before activation.
        seedHeldDeposit(leaseId);
        performAction(leaseId, "ACTIVATE");

        mockMvc.perform(get("/api/v1/leases/" + leaseId)
                        .with(MockTenantAuthentication.asTenant(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }
}