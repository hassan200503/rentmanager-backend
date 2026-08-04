package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Domain invariants for the premises classification audit trail:
 * an explicit override always carries a reason + actor, auto-derivation
 * carries neither, and changeType() keeps the classification consistent.
 */
class PropertyClassificationOverrideTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private Property autoClassified(PropertyType type) {
        return Property.create(
                TENANT_ID, "Green Villa", type,
                PropertyTestFactory.address(), PropertyTestFactory.geo(),
                PropertyTestFactory.dimensions(), "Desc", "corr-1"
        );
    }

    private Property overridden(PremisesType premises, String reason) {
        return Property.create(
                TENANT_ID, "Green Villa", PropertyType.APARTMENT,
                premises, reason, USER_ID,
                PropertyTestFactory.address(), PropertyTestFactory.geo(),
                PropertyTestFactory.dimensions(), "Desc", "corr-1"
        );
    }

    @Test
    void autoClassifiedPropertyDerivesPremisesAndRecordsNoAudit() {
        Property property = autoClassified(PropertyType.WAREHOUSE);

        assertEquals(PremisesType.COMMERCIAL, property.getPremisesType());
        assertNull(property.getPremisesTypeOverrideReason());
        assertNull(property.getPremisesTypeChangedBy());
        assertNull(property.getPremisesTypeChangedAt());
    }

    @Test
    void explicitOverrideRequiresReasonAndChangedBy() {
        assertThrows(IllegalArgumentException.class,
                () -> overridden(PremisesType.MIXED_USE, null));
        assertThrows(IllegalArgumentException.class,
                () -> overridden(PremisesType.MIXED_USE, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> Property.create(
                        TENANT_ID, "Green Villa", PropertyType.APARTMENT,
                        PremisesType.RESIDENTIAL, "Because", null,
                        PropertyTestFactory.address(), PropertyTestFactory.geo(),
                        PropertyTestFactory.dimensions(), "Desc", "corr-1"));
    }

    @Test
    void explicitOverrideStampsAuditTrail() {
        Property property = overridden(PremisesType.MIXED_USE, "Shops below, flats above");

        assertEquals(PremisesType.MIXED_USE, property.getPremisesType());
        assertEquals("Shops below, flats above", property.getPremisesTypeOverrideReason());
        assertEquals(USER_ID, property.getPremisesTypeChangedBy());
        assertNotNull(property.getPremisesTypeChangedAt());
    }

    @Test
    void reasonWithoutPremisesTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Property.create(
                        TENANT_ID, "Green Villa", PropertyType.APARTMENT,
                        null, "Because", USER_ID,
                        PropertyTestFactory.address(), PropertyTestFactory.geo(),
                        PropertyTestFactory.dimensions(), "Desc", "corr-1"));
    }

    @Test
    void overrideReasonIsLengthLimited() {
        String tooLong = "x".repeat(501);
        assertThrows(IllegalArgumentException.class,
                () -> overridden(PremisesType.COMMERCIAL, tooLong));
    }

    @Test
    void changeTypeReDerivesWhenNoOverrideExists() {
        Property property = autoClassified(PropertyType.APARTMENT);
        assertEquals(PremisesType.RESIDENTIAL, property.getPremisesType());

        property.changeType(PropertyType.WAREHOUSE, "corr-2");

        assertEquals(PropertyType.WAREHOUSE, property.getPropertyType());
        assertEquals(PremisesType.COMMERCIAL, property.getPremisesType());
        assertNull(property.getPremisesTypeOverrideReason());
        assertNull(property.getPremisesTypeChangedBy());
        assertNull(property.getPremisesTypeChangedAt());
    }

    @Test
    void changeTypeKeepsAuditedOverrideSticky() {
        Property property = overridden(PremisesType.MIXED_USE, "Shops below, flats above");

        property.changeType(PropertyType.WAREHOUSE, "corr-2");

        assertEquals(PropertyType.WAREHOUSE, property.getPropertyType());
        assertEquals(PremisesType.MIXED_USE, property.getPremisesType());
        assertEquals("Shops below, flats above", property.getPremisesTypeOverrideReason());
    }

    @Test
    void changeTypeRejectsNullType() {
        Property property = autoClassified(PropertyType.APARTMENT);
        assertThrows(IllegalArgumentException.class,
                () -> property.changeType(null, "corr-2"));
    }

    @Test
    void rehydrateRoundTripsOverrideAuditFields() {
        Instant changedAt = Instant.parse("2026-08-04T10:00:00Z");
        UUID id = UUID.randomUUID();

        Property property = Property.rehydrate(
                id, TENANT_ID, "Green Villa", "PROP-" + id,
                PropertyType.APARTMENT, PremisesType.MIXED_USE,
                "Shops below, flats above", USER_ID, changedAt,
                PropertyStatus.ACTIVE, OccupancyStatus.FULLY_OCCUPIED,
                PropertyTestFactory.address(), PropertyTestFactory.geo(),
                PropertyTestFactory.dimensions(), "Desc"
        );

        assertEquals(PremisesType.MIXED_USE, property.getPremisesType());
        assertEquals("Shops below, flats above", property.getPremisesTypeOverrideReason());
        assertEquals(USER_ID, property.getPremisesTypeChangedBy());
        assertEquals(changedAt, property.getPremisesTypeChangedAt());
    }
}
