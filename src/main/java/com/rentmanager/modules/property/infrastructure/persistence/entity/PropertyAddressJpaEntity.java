package com.rentmanager.modules.property.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * JPA Embeddable Value Object for Property Address.
 *
 * ARCHITECTURE RULES:
 * - NOT an Entity
 * - MUST NOT have @Id
 * - MUST NOT have a repository
 * - MUST ONLY be embedded inside PropertyJpaEntity
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PropertyAddressJpaEntity implements Serializable {

    @Column(name = "address_line_1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line_2")
    private String addressLine2;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "country", nullable = false)
    private String country;

    /**
     * Optional helper constructor for safe manual creation
     * (useful for mappers / builders)
     */
    public static PropertyAddressJpaEntity of(
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postalCode,
            String country
    ) {
        return PropertyAddressJpaEntity.builder()
                .addressLine1(addressLine1)
                .addressLine2(addressLine2)
                .city(city)
                .state(state)
                .postalCode(postalCode)
                .country(country)
                .build();
    }
}