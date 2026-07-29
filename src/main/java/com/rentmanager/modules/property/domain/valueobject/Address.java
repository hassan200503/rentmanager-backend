package com.rentmanager.modules.property.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Address {

    @Column(name = "street_address", nullable = false)
    private String streetAddress;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "country", nullable = false)
    private String country;

    // --------------------------------------------------
    // FACTORY METHOD (SAFE CONSTRUCTION)
    // --------------------------------------------------

    public static Address create(
            String streetAddress,
            String city,
            String state,
            String postalCode,
            String country
    ) {

        validate(streetAddress, "Street address");
        validate(city, "City");
        validate(country, "Country");

        return Address.builder()
                .streetAddress(streetAddress.trim())
                .city(city.trim())
                .state(state != null ? state.trim() : null)
                .postalCode(postalCode != null ? postalCode.trim() : null)
                .country(country.trim())
                .build();
    }

    // --------------------------------------------------
    // VALIDATION
    // --------------------------------------------------

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(streetAddress);
        if (state != null && !state.isBlank()) {
            sb.append(", ").append(state);
        }
        sb.append(", ").append(city);
        if (postalCode != null && !postalCode.isBlank()) {
            sb.append(" ").append(postalCode);
        }
        sb.append(", ").append(country);
        return sb.toString();
    }

    private static void validate(String value, String fieldName) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " is required"
            );
        }
    }
}