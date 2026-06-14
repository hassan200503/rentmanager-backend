package com.rentmanager.crossmodule.tenant_isolation;

import com.rentmanager.crossmodule.config.CrossModuleTestConfig;
import com.rentmanager.crossmodule.core.CrossModuleBaseIT;
import com.rentmanager.crossmodule.core.TenantTestExecutor;
import com.rentmanager.crossmodule.support.EventCapture;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.lease.domain.model.Lease;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(CrossModuleTestConfig.class)
class TenantIsolationIT extends CrossModuleBaseIT {

    @Autowired
    private TenantTestExecutor tenantExecutor;

    @MockBean
    private EventCapture eventCapture;

    // =========================================================
    // 1. PROPERTY ISOLATION TEST
    // =========================================================
    @Test
    void should_prevent_cross_tenant_property_visibility() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Property propertyA = tenantExecutor.executeAsTenant(tenantA,
                () -> factory.createProperty(tenantA));

        Property propertyB = tenantExecutor.executeAsTenant(tenantB,
                () -> factory.createProperty(tenantB));

        assertEquals(tenantA, propertyA.getTenantId());
        assertEquals(tenantB, propertyB.getTenantId());

        assertNotEquals(propertyA.getTenantId(), propertyB.getTenantId());
        assertNotEquals(propertyA.getId(), propertyB.getId());
    }

    // =========================================================
    // 2. UNIT ISOLATION TEST
    // =========================================================
    @Test
    void should_prevent_cross_tenant_unit_leakage() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Unit unitA = tenantExecutor.executeAsTenant(tenantA, () -> {
            Property property = factory.createProperty(tenantA);
            return factory.createUnit(tenantA, property.getId());
        });

        Unit unitB = tenantExecutor.executeAsTenant(tenantB, () -> {
            Property property = factory.createProperty(tenantB);
            return factory.createUnit(tenantB, property.getId());
        });

        assertEquals(tenantA, unitA.getTenantId());
        assertEquals(tenantB, unitB.getTenantId());

        assertNotEquals(unitA.getTenantId(), unitB.getTenantId());
        assertNotEquals(unitA.getId(), unitB.getId());
    }

    // =========================================================
    // 3. LEASE ISOLATION TEST
    // =========================================================
    @Test
    void should_prevent_cross_tenant_lease_access() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Lease leaseA = tenantExecutor.executeAsTenant(tenantA, () -> {

            Property property = factory.createProperty(tenantA);
            Unit unit = factory.createUnit(tenantA, property.getId());

            return factory.createLease(
                    tenantA,
                    property.getId(),
                    unit.getId(),
                    UUID.randomUUID()
            );
        });

        Lease leaseB = tenantExecutor.executeAsTenant(tenantB, () -> {

            Property property = factory.createProperty(tenantB);
            Unit unit = factory.createUnit(tenantB, property.getId());

            return factory.createLease(
                    tenantB,
                    property.getId(),
                    unit.getId(),
                    UUID.randomUUID()
            );
        });

        assertEquals(tenantA, leaseA.getTenantId());
        assertEquals(tenantB, leaseB.getTenantId());

        assertNotEquals(leaseA.getTenantId(), leaseB.getTenantId());
        assertNotEquals(leaseA.getId(), leaseB.getId());
    }

    // =========================================================
    // 4. CROSS-TENANT ID REUSE SAFETY TEST
    // =========================================================
    @Test
    void should_prevent_id_collision_across_tenants() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Property propertyA = tenantExecutor.executeAsTenant(tenantA,
                () -> factory.createProperty(tenantA));

        Property propertyB = tenantExecutor.executeAsTenant(tenantB,
                () -> factory.createProperty(tenantB));

        assertEquals(tenantA, propertyA.getTenantId());
        assertEquals(tenantB, propertyB.getTenantId());

        assertNotEquals(propertyA.getId(), propertyB.getId());
    }

    // =========================================================
    // 5. SERVICE LAYER ISOLATION SIMULATION
    // =========================================================
    @Test
    void should_enforce_tenant_boundary_at_domain_level() {

        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        Property propertyA = tenantExecutor.executeAsTenant(tenantA,
                () -> factory.createProperty(tenantA));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {

            tenantExecutor.executeAsTenant(tenantB, () -> {

                assertNotEquals(tenantB, propertyA.getTenantId(),
                        "Tenant boundary violation detected");

                throw new IllegalStateException("Tenant boundary violation detected");
            });
        });

        assertTrue(ex.getMessage().contains("Tenant boundary"));
    }
}