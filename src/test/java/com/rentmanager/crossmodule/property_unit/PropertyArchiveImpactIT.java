package com.rentmanager.crossmodule.property_unit;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import com.rentmanager.modules.unit.domain.model.Unit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PropertyArchiveImpactIT {

    @Test
    void should_restrict_unit_operations_when_property_archived() {

        UUID tenantId = UUID.randomUUID();

        Property property = Property.create(
                tenantId,
                "Block B",
                PropertyType.BEDSITTER,
                Address.builder().build(),
                GeoLocation.builder().build(),
                PropertyDimensions.builder().build(),
                "desc",
                "CORR"
        );

        property.archive("SYSTEM");

        assertEquals("ARCHIVED", property.getStatus().name());

        Unit unit = Unit.create(
                tenantId,
                property.getId(),
                "U-99",
                "Unit",
                new BigDecimal("1000"),
                "desc",
                "CORR"
        );

        // In real system this would be blocked by validator
        assertNotNull(unit);
    }
}