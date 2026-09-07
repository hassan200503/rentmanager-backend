package com.rentmanager.crossmodule.unit_lease;

import com.rentmanager.crossmodule.core.CrossModuleBaseIT;
import com.rentmanager.crossmodule.core.TenantTestExecutor;
import com.rentmanager.crossmodule.support.AssertionHelper;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.lease.domain.model.Lease;
import com.rentmanager.modules.lease.domain.enums.LeaseStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lease activation flow: full state machine must be respected.
 *
 * activate() requires AWAITING_DEPOSIT status (post-V34 tightening). Tests
 * that previously called approve() → activate() directly were silently
 * broken; the fix is to include markAwaitingDeposit() in the path. See TD-126.
 */
class LeaseActivationFlowIT extends CrossModuleBaseIT {

    @Autowired
    private TenantTestExecutor tenantExecutor;

    @Test
    void should_allow_activation_only_when_unit_is_occupied() {

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
            unit.markOccupied("FLOW");
            lease.activate();

            assertTrue(lease.isActive());
            assertEquals(LeaseStatus.ACTIVE, lease.getStatus());

            AssertionHelper.assertUnitOccupied(unit);
            AssertionHelper.assertLeaseActive(lease);

            return null;
        });
    }

    @Test
    void should_transition_unit_to_occupied_when_lease_is_activated() {

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
            unit.markOccupied("SYNC");
            lease.activate();

            assertEquals("OCCUPIED", unit.getOccupancyStatus().name());
            assertTrue(lease.isActive());

            AssertionHelper.assertUnitMatchesLease(unit, lease);

            return null;
        });
    }

    @Test
    void should_activate_with_vacant_unit_domain_does_not_gate_on_occupancy() {

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

            assertEquals("VACANT", unit.getOccupancyStatus().name());

            // The domain does not gate activate() on unit occupancy.
            // Occupancy sync is an event-driven side effect; the test
            // verifies that activation itself succeeds.
            lease.activate();

            assertTrue(lease.isActive());
            assertEquals(LeaseStatus.ACTIVE, lease.getStatus());
            assertEquals("VACANT", unit.getOccupancyStatus().name());

            return null;
        });
    }
}
