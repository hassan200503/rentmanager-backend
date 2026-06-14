package com.rentmanager.crossmodule.support;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.lease.domain.model.Lease;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SaaS-grade domain assertion engine.
 *
 * Ensures cross-module invariants are validated consistently.
 */
public class AssertionHelper {

    // =====================================================
    // PROPERTY ASSERTIONS
    // =====================================================

    public static void assertPropertyActive(Property property) {
        assertNotNull(property);
        assertEquals("ACTIVE", property.getStatus().name());
    }

    public static void assertPropertyArchived(Property property) {
        assertNotNull(property);
        assertEquals("ARCHIVED", property.getStatus().name());
    }

    // =====================================================
    // UNIT ASSERTIONS
    // =====================================================

    public static void assertUnitOccupied(Unit unit) {
        assertNotNull(unit);
        assertEquals("OCCUPIED", unit.getOccupancyStatus().name());
    }

    public static void assertUnitVacant(Unit unit) {
        assertNotNull(unit);
        assertEquals("VACANT", unit.getOccupancyStatus().name());
    }

    public static void assertUnitArchived(Unit unit) {
        assertNotNull(unit);
        assertEquals("ARCHIVED", unit.getStatus().name());
    }

    // =====================================================
    // LEASE ASSERTIONS
    // =====================================================

    public static void assertLeaseActive(Lease lease) {
        assertNotNull(lease);
        assertTrue(lease.isActive());
    }

    public static void assertLeaseTerminated(Lease lease) {
        assertNotNull(lease);
        assertTrue(lease.isTerminated());
    }

    public static void assertLeaseExpired(Lease lease) {
        assertNotNull(lease);
        assertTrue(lease.isExpired());
    }

    // =====================================================
    // CROSS-MODULE ASSERTIONS
    // =====================================================

    public static void assertUnitMatchesLease(Unit unit, Lease lease) {
        assertEquals(unit.getId(), lease.getUnitId());
    }

    public static void assertPropertyMatchesLease(Property property, Lease lease) {
        assertEquals(property.getId(), lease.getPropertyId());
    }
}