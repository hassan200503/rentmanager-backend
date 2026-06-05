package com.rentmanager.modules.property.domain;

import com.rentmanager.modules.property.domain.valueobject.PropertyDimensions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDimensionsTest {

    @Test
    void shouldCalculateOccupancyRatio() {

        PropertyDimensions dims = PropertyDimensions.create(
                100.0,
                50.0,
                10
        );

        assertEquals(0.5, dims.occupancyRatio());
    }

    @Test
    void shouldDetectFullyOccupied() {

        PropertyDimensions dims = PropertyDimensions.create(
                100.0,
                100.0,
                10
        );

        assertTrue(dims.isFullyOccupied());
    }

    @Test
    void shouldRejectInvalidArea() {

        assertThrows(IllegalArgumentException.class, () ->
                PropertyDimensions.create(100.0, 200.0, 5)
        );
    }
}