package com.rentmanager.modules.property.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GeoLocation {

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    // --------------------------------------------------
    // FACTORY METHOD (SAFE CONSTRUCTION)
    // --------------------------------------------------

    public static GeoLocation create(BigDecimal latitude, BigDecimal longitude) {

        validate(latitude, longitude);

        return GeoLocation.builder()
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }

    // --------------------------------------------------
    // VALIDATION
    // --------------------------------------------------

    private static void validate(BigDecimal lat, BigDecimal lng) {

        if (lat == null || lng == null) {
            throw new IllegalArgumentException("Latitude and longitude are required");
        }

        if (lat.compareTo(BigDecimal.valueOf(90)) > 0 ||
                lat.compareTo(BigDecimal.valueOf(-90)) < 0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }

        if (lng.compareTo(BigDecimal.valueOf(180)) > 0 ||
                lng.compareTo(BigDecimal.valueOf(-180)) < 0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }
}