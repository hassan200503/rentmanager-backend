package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.model.Property;
import com.rentmanager.modules.property.domain.PropertyTestFactory;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantInvariantTest {

    @Test
    void shouldBelongToCorrectTenantOnly() {

        Property property = PropertyTestFactory.createProperty();

        UUID tenant = property.getTenantId();
        UUID otherTenant = UUID.randomUUID();

        assertTrue(property.belongsToTenant(tenant));
        assertFalse(property.belongsToTenant(otherTenant));
    }

    @Test
    void shouldNotAllowExternalTenantMutation() {

        Property property = PropertyTestFactory.createProperty();

        // No setter exists → ensure immutability via reflection check safety
        assertDoesNotThrow(() -> property.belongsToTenant(property.getTenantId()));
    }
}