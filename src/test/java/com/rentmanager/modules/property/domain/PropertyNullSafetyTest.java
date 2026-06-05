package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.PropertyTestFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyNullSafetyTest {

    @Test
    void shouldIgnoreNullNameUpdate() {

        Property property = PropertyTestFactory.createProperty();

        String original = property.getName();

        property.updateDetails(null, null);

        assertEquals(original, property.getName());
    }

    @Test
    void shouldIgnoreBlankNameUpdate() {

        Property property = PropertyTestFactory.createProperty();

        String original = property.getName();

        property.updateDetails("   ", "desc");

        assertEquals(original, property.getName());
    }

    @Test
    void shouldAllowDescriptionUpdateOnly() {

        Property property = PropertyTestFactory.createProperty();

        property.updateDetails(null, "New Desc");

        assertEquals("New Desc", property.getDescription());
    }
}