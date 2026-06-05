package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.valueobject.*;

import java.math.BigDecimal;
import java.util.UUID;

public class PropertyTestFactory {

    public static GeoLocation geo() {
        return GeoLocation.create(
                new BigDecimal("1.2921"),
                new BigDecimal("36.8219")
        );
    }

    public static Address address() {
        return new Address(
                "Kenya",
                "Nairobi",
                "Westlands",
                "00100",
                "Zone A"
        );
    }

    public static PropertyDimensions dimensions() {
        return PropertyDimensions.create(
                100.0,
                50.0,
                5
        );
    }

    public static Property createProperty() {
        return Property.create(
                UUID.randomUUID(),
                "Alpha Building",
                PropertyType.APARTMENT,
                address(),
                geo(),
                dimensions(),
                "Modern building",
                "corr-1"
        );
    }
}