package com.rentmanager.modules.property.application;

import com.rentmanager.modules.property.application.dto.response.PropertyTypeDescriptor;
import com.rentmanager.modules.property.application.dto.response.PropertyTypeMetadataResponse;
import com.rentmanager.modules.property.application.mapper.PropertyMapper;
import com.rentmanager.modules.property.application.query.service.PropertyQueryServiceImpl;
import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * The taxonomy endpoint is the single source of truth rendered by the
 * property form: every PropertyType appears exactly once with its derived
 * classification (never MIXED_USE), and the override list is stable and
 * includes MIXED_USE.
 */
class PropertyTaxonomyMetadataTest {

    private PropertyQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PropertyQueryServiceImpl(
                mock(PropertyRepository.class),
                mock(PropertyMapper.class)
        );
    }

    @Test
    void metadataCoversEveryPropertyTypeExactlyOnce() {
        PropertyTypeMetadataResponse metadata = service.getPropertyTypes();

        assertNotNull(metadata.getPropertyTypes());
        assertEquals(PropertyType.values().length, metadata.getPropertyTypes().size());

        for (PropertyType type : PropertyType.values()) {
            long matches = metadata.getPropertyTypes().stream()
                    .filter(descriptor -> descriptor.propertyType() == type)
                    .count();
            assertEquals(1, matches, "Exactly one descriptor for " + type);
        }
    }

    @Test
    void derivedClassificationNeverMixedUse() {
        for (PropertyTypeDescriptor descriptor : service.getPropertyTypes().getPropertyTypes()) {
            assertEquals(false,
                    PremisesType.MIXED_USE == descriptor.derivedPremisesType(),
                    "Derivation for " + descriptor.propertyType() + " must never be MIXED_USE");
            assertEquals(
                    PremisesType.fromPropertyType(descriptor.propertyType()),
                    descriptor.derivedPremisesType(),
                    "Metadata must match the domain derivation rule"
            );
        }
    }

    @Test
    void premisesOverrideListIsStableAndIncludesMixedUse() {
        assertEquals(
                java.util.List.of(PremisesType.RESIDENTIAL, PremisesType.COMMERCIAL, PremisesType.MIXED_USE),
                service.getPropertyTypes().getPremisesTypes()
        );
    }
}
