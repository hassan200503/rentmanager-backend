package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.enums.*;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.PropertyTestFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDomainTest {

    @Test
    void shouldCreatePropertyInInitialState() {

        Property property = PropertyTestFactory.createProperty();

        assertEquals(PropertyStatus.DRAFT, property.getStatus());
        assertEquals(OccupancyStatus.VACANT, property.getOccupancyStatus());
        assertEquals("Alpha Building", property.getName());
    }

    @Test
    void shouldActivateProperty() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("corr-2");

        assertEquals(PropertyStatus.ACTIVE, property.getStatus());
    }

    @Test
    void shouldArchiveProperty() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("corr-2");
        property.archive("corr-3");

        assertTrue(property.isArchived());
    }

    @Test
    void shouldUpdateDetailsSafely() {

        Property property = PropertyTestFactory.createProperty();

        property.updateDetails("New Name", null);

        assertEquals("New Name", property.getName());
    }
}