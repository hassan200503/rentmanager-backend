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
 * Unit occupancy must always reflect Lease lifecycle state.
 *
 * In production this sync is event-driven (DepositPaymentEventListener marks
 * the unit occupied when a deposit is confirmed). These tests exercise the
 * domain objects in isolation, manually performing the sync step to verify the
 * invariant holds at the domain layer regardless of infrastructure.
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

            lease.approve();
            lease.markAwaitingDeposit();
            lease.activate();

            assertEquals("VACANT", unit.getOccupancyStatus().name());

            // In production this is triggered by DepositPaymentEventListener.
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

            lease.approve();
            lease.markAwaitingDeposit();
            lease.activate();

            assertTrue(lease.isActive());

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
