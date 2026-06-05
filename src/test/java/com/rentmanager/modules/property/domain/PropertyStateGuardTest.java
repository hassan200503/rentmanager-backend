package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.enums.PropertyStatus;
import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.PropertyTestFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyStateGuardTest {

    @Test
    void shouldIgnoreDuplicateActivation() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("corr-1");
        property.activate("corr-2");

        assertEquals(PropertyStatus.ACTIVE, property.getStatus());
    }

    @Test
    void shouldIgnoreDuplicateArchive() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("corr-1");
        property.archive("corr-2");
        property.archive("corr-3");

        assertTrue(property.isArchived());
    }

    @Test
    void shouldMaintainStableStateAfterArchive() {

        Property property = PropertyTestFactory.createProperty();

        property.activate("corr-1");
        property.archive("corr-2");

        assertEquals(PropertyStatus.ARCHIVED, property.getStatus());
    }
}