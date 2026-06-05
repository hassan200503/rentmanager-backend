package com.rentmanager.modules.property.api.routes;

public final class PropertyRoutes {

    private PropertyRoutes() {}

    public static final String BASE = "/api/v1/properties";

    public static final String BY_ID = BASE + "/{propertyId}";
    public static final String ACTIVATE = BASE + "/{propertyId}/activate";
    public static final String ARCHIVE = BASE + "/{propertyId}/archive";
}