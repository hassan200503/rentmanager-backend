package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.enums.OccupancyStatus;
import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.PropertyTestFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyConsistencyTest {

    @Test
    void archivedPropertyShouldRemainArchivedEvenAfterOperations() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("c1");
        property.markFullyOccupied("c2");
        property.archive("c3");

        assertTrue(property.isArchived());
        assertNotEquals(PropertyStatus.ACTIVE, property.getStatus());
    }

    @Test
    void occupancyShouldBeIndependentOfStatus() {

        Property property = PropertyTestFactory.createProperty();

        property.markFullyOccupied("c1");
        property.archive("c2");

        assertEquals(OccupancyStatus.FULLY_OCCUPIED, property.getOccupancyStatus());
        assertTrue(property.isArchived());
    }
}