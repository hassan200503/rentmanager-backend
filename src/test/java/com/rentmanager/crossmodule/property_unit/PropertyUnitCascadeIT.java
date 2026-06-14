package com.rentmanager.crossmodule.property_unit;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.unit.domain.model.Unit;
import com.rentmanager.modules.property.domain.enums.*;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PropertyUnitCascadeIT {

    @Test
    void should_allow_unit_creation_only_for_active_property_context() {

        UUID tenantId = UUID.randomUUID();

        Property property = Property.create(
                tenantId,
                "Building A",
                PropertyType.BEDSITTER,
                Address.builder().build(),
                GeoLocation.builder().build(),
                PropertyDimensions.builder().build(),
                "desc",
                "CORR"
        );

        property.activate("SYSTEM");

        assertEquals(PropertyStatus.ACTIVE, property.getStatus());

        Unit unit = Unit.create(
                tenantId,
                property.getId(),
                "U-1",
                "Unit 1",
                new BigDecimal("1000"),
                "desc",
                "CORR"
        );

        assertEquals(property.getId(), unit.getPropertyId());
    }
}