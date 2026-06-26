package com.rentmanager.modules.property.application.dto.response;

import com.rentmanager.modules.property.domain.enums.PropertyType;
import com.rentmanager.modules.property.domain.valueobject.Address;
import com.rentmanager.modules.property.domain.valueobject.GeoLocation;
import lombok.Builder;
import lombok.Getter;

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
}