package com.rentmanager.modules.property.domain.model;

import java.util.UUID;

public class PropertyAddress {

    private UUID id;
    private String streetAddress;
    private String city;
    private String state;
    private String country;

    public PropertyAddress() {}

    public PropertyAddress(UUID id, String streetAddress, String city, String state, String country) {
        this.id = id;
        this.streetAddress = streetAddress;
        this.city = city;
        this.state = state;
        this.country = country;
    }

    public UUID getId() {
        return id;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public String getCity() {
        return city;
    }

    public String getState() {
        return state;
    }

    public String getCountry() {
        return country;
    }
}