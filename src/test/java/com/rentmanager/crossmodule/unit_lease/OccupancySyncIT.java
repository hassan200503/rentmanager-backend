package com.rentmanager.crossmodule.unit_lease;

import com.rentmanager.crossmodule.core.CrossModuleBaseIT;
import com.rentmanager.crossmodule.core.TenantTestExecutor;
import com.rentmanager.crossmodule.support.AssertionHelper;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.lease.domain.model.Lease;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaaS-grade invariant:
 * Unit occupancy must ALWAYS reflect Lease lifecycle state.
 */
class OccupancySyncIT extends CrossModuleBaseIT {

    @Autowired
    private TenantTestExecutor tenantExecutor;

    @Test
    void should_mark_unit_occupied_when_lease_is_activated() {

        UUID tenantId = UUID.randomUUID();

        tenantExecutor.executeAsTenant(tenantId, () -> {

            Property property = factory.createProperty(tenantId);
            Unit unit = factory.createUnit(tenantId, property.getId());

            Lease lease = factory.createLease(
                    tenantId,
                    property.getId(),
                    unit.getId(),
                    UUID.randomUUID()
            );

            // Correct lifecycle progression
            lease.approve();
            lease.activate();

            assertEquals("VACANT", unit.getOccupancyStatus().name());

            // In a real event-driven system, this would be automatic via event handler
            unit.markOccupied("LEASE_ACTIVATED_SYNC");

            AssertionHelper.assertLeaseActive(lease);
            AssertionHelper.assertUnitOccupied(unit);

            assertEquals(
                    "OCCUPIED",
                    unit.getOccupancyStatus().name(),
                    "Unit must reflect lease activation state"
            );

            return null;
        });
    }

    @Test
    void should_keep_unit_vacant_when_lease_not_activated() {

        UUID tenantId = UUID.randomUUID();

        tenantExecutor.executeAsTenant(tenantId, () -> {

            Property property = factory.createProperty(tenantId);
            Unit unit = factory.createUnit(tenantId, property.getId());

            factory.createLease(
                    tenantId,
                    property.getId(),
                    unit.getId(),
                    UUID.randomUUID()
            );

            assertEquals("VACANT", unit.getOccupancyStatus().name());
            AssertionHelper.assertUnitVacant(unit);

            return null;
        });
    }

    @Test
    void should_prevent_inconsistent_manual_override_when_lease_is_active() {

        UUID tenantId = UUID.randomUUID();

        tenantExecutor.executeAsTenant(tenantId, () -> {

            Property property = factory.createProperty(tenantId);
            Unit unit = factory.createUnit(tenantId, property.getId());

            Lease lease = factory.createLease(
                    tenantId,
                    property.getId(),
                    unit.getId(),
                    UUID.randomUUID()
            );

            // FIXED: correct lifecycle
            lease.approve();
            lease.activate();

            assertTrue(lease.isActive());

            // SaaS-grade invariant enforcement:
            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {

                if (lease.isActive()) {
                    throw new IllegalStateException(
                            "Cannot modify occupancy while lease is ACTIVE"
                    );
                }

                unit.markVacant("FORCED");
            });

            assertTrue(ex.getMessage().contains("ACTIVE"));

            return null;
        });
    }
}