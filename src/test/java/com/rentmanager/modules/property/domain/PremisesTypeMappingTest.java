package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.enums.PremisesType;
import com.rentmanager.modules.property.domain.enums.PropertyType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Locks the auto-derivation rule (COMMERCIAL/OFFICE/WAREHOUSE -> COMMERCIAL,
 * everything else -> RESIDENTIAL) as a contract: the frontend renders this
 * mapping from the taxonomy endpoint, so a change here must be mirrored in
 * the metadata response only — never in client code.
 */
class PremisesTypeMappingTest {

    @Test
    void commercialTypesDeriveToCommercial() {
        assertEquals(PremisesType.COMMERCIAL, PremisesType.fromPropertyType(PropertyType.COMMERCIAL));
        assertEquals(PremisesType.COMMERCIAL, PremisesType.fromPropertyType(PropertyType.OFFICE));
        assertEquals(PremisesType.COMMERCIAL, PremisesType.fromPropertyType(PropertyType.WAREHOUSE));
    }

    @Test
    void residentialTypesDeriveToResidential() {
        assertEquals(PremisesType.RESIDENTIAL, PremisesType.fromPropertyType(PropertyType.APARTMENT));
        assertEquals(PremisesType.RESIDENTIAL, PremisesType.fromPropertyType(PropertyType.BEDSITTER));
        assertEquals(PremisesType.RESIDENTIAL, PremisesType.fromPropertyType(PropertyType.STUDIO));
        assertEquals(PremisesType.RESIDENTIAL, PremisesType.fromPropertyType(PropertyType.MAISONETTE));
        assertEquals(PremisesType.RESIDENTIAL, PremisesType.fromPropertyType(PropertyType.VILLA));
        assertEquals(PremisesType.RESIDENTIAL, PremisesType.fromPropertyType(PropertyType.HOSTEL));
        assertEquals(PremisesType.RESIDENTIAL, PremisesType.fromPropertyType(PropertyType.AIRBNB));
    }

    @Test
    void mixedUseIsNeverAutoDerived() {
        for (PropertyType type : PropertyType.values()) {
            assertEquals(false,
                    PremisesType.MIXED_USE == PremisesType.fromPropertyType(type),
                    "MIXED_USE must never be derived from " + type);
        }
    }

    @Test
    void nullTypeDerivesToNull() {
        assertNull(PremisesType.fromPropertyType(null));
    }
}
