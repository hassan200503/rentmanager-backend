package com.rentmanager.modules.property.application.dto.response;

import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PublicPropertyResponse {
    private UUID propertyId;
    private String name;
    private PropertyType propertyType;
    private Address address;
    private GeoLocation geoLocation;
    private String description;

    private List<String> images; // 👈 add this

    /**
     * How many units a renter could enquire about today.
     *
     * <p>The listings page is titled "Available Properties" and promises "real
     * vacancies, not stale listings", but every card looked identical whether
     * the property had five homes free or none — the renter only found out by
     * clicking through. This is that promise, made checkable.
     */
    private Integer availableUnits;

    /**
     * Asking rent of the cheapest and dearest available unit.
     *
     * <p>Null when nothing is available, rather than zero: a card must be able
     * to say nothing at all instead of quoting a price of KES 0. Serialised as
     * a JSON string like every other BigDecimal in this codebase.
     */
    private BigDecimal minRent;

    private BigDecimal maxRent;
}