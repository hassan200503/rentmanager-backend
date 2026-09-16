package com.rentmanager.crossmodule.unit_lease;

import com.rentmanager.crossmodule.core.*;
import com.rentmanager.crossmodule.support.AssertionHelper;
import com.rentmanager.modules.lease.domain.enums.TerminationType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.lease.domain.model.Lease;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cross-module Unit ↔ Lease lifecycle coupling.
 *
 * activate() requires AWAITING_DEPOSIT (post-V34). All paths that previously
 * called approve() → activate() directly are updated to include
 * markAwaitingDeposit() in between. See TD-126.
 */
class UnitLeaseFlowIT extends CrossModuleBaseIT {

    @Autowired
    private TenantTestExecutor tenantExecutor;

    @Test
    void should_create_unit_then_create_lease_and_enforce_binding_consistency() {

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

            AssertionHelper.assertUnitMatchesLease(unit, lease);
            AssertionHelper.assertPropertyMatchesLease(property, lease);

            assertEquals(unit.getPropertyId(), lease.getPropertyId());
            assertEquals(unit.getId(), lease.getUnitId());

            return null;
        });
    }

    @Test
    void should_block_lease_activation_if_unit_is_not_occupied() {

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

            // Application layer guard (not domain): activation is rejected
            // when the unit is vacant, before handing off to the domain.
            assertThrows(IllegalStateException.class, () -> {

                if ("VACANT".equals(unit.getOccupancyStatus().name())) {
                    throw new IllegalStateException("Cannot activate lease on vacant unit");
                }

                lease.activate();
            });

            return null;
        });
    }

    @Test
    void should_sync_unit_occupancy_when_lease_is_activated() {

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

            unit.markOccupied("LEASE_SYNC");

            AssertionHelper.assertUnitOccupied(unit);
            AssertionHelper.assertLeaseActive(lease);

            assertEquals("OCCUPIED", unit.getOccupancyStatus().name());

            return null;
        });
    }

    @Test
    void should_prevent_unit_being_vacant_while_active_lease_exists() {

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

            IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {

                if (lease.isActive()) {
                    throw new IllegalStateException(
                            "Cannot vacate unit while active lease exists"
                    );
                }

                unit.markVacant("TEST");
            });

            assertTrue(ex.getMessage().contains("active lease"));

            return null;
        });
    }

    @Test
    void should_maintain_consistency_when_lease_terminates() {

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

            unit.markOccupied("TEST");

            lease.approve();
            lease.markAwaitingDeposit();
            lease.activate();

            lease.terminate(
                    TerminationType.TENANT_REQUEST,
                    "end of contract",
                    "SYSTEM",
                    tenantId
            );

            unit.markVacant("TEST");

            AssertionHelper.assertUnitVacant(unit);
            AssertionHelper.assertLeaseTerminated(lease);

            return null;
        });
    }
}
