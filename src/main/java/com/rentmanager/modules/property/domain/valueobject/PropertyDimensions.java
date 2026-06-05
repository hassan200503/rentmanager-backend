package com.rentmanager.modules.property.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PropertyDimensions {

    @Column(name = "total_area")
    private Double totalArea;

    @Column(name = "occupied_area")
    private Double occupiedArea;

    @Column(name = "unit_count")
    private Integer unitCount;

    // --------------------------------------------------
    // FACTORY METHOD (SAFE CONSTRUCTION)
    // --------------------------------------------------

    public static PropertyDimensions create(
            Double totalArea,
            Double occupiedArea,
            Integer unitCount
    ) {

        validate(totalArea, occupiedArea, unitCount);

        return PropertyDimensions.builder()
                .totalArea(totalArea)
                .occupiedArea(occupiedArea)
                .unitCount(unitCount)
                .build();
    }

    // --------------------------------------------------
    // DOMAIN LOGIC
    // --------------------------------------------------

    public double occupancyRatio() {

        if (totalArea == null || totalArea == 0) {
            return 0;
        }

        if (occupiedArea == null) {
            return 0;
        }

        return occupiedArea / totalArea;
    }

    public boolean isFullyOccupied() {

        return totalArea != null
                && occupiedArea != null
                && occupiedArea >= totalArea;
    }

    // --------------------------------------------------
    // VALIDATION
    // --------------------------------------------------

    private static void validate(
            Double totalArea,
            Double occupiedArea,
            Integer unitCount
    ) {

        if (totalArea == null || totalArea <= 0) {
            throw new IllegalArgumentException("Total area must be greater than 0");
        }

        if (occupiedArea == null || occupiedArea < 0) {
            throw new IllegalArgumentException("Occupied area cannot be negative");
        }

        if (occupiedArea > totalArea) {
            throw new IllegalArgumentException("Occupied area cannot exceed total area");
        }

        if (unitCount == null || unitCount < 0) {
            throw new IllegalArgumentException("Unit count cannot be negative");
        }
    }
}